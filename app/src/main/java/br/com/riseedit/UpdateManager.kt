package br.com.riseedit

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.FileProvider
import br.com.riseedit.core.UpdatePolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

data class AppRelease(val tag: String, val notes: String, val assetId: Long, val size: Long, val sha256: String)

/** Read-only credential stays on this device, encrypted with a non-exportable Android Keystore key. */
class UpdateManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    var automatic: Boolean
        get() = prefs.getBoolean("automatic", true)
        set(value) { prefs.edit().putBoolean("automatic", value).apply() }
    val hasToken get() = prefs.contains("token")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("rise-github", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("rise-github", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(token.trim().toByteArray())
        prefs.edit().putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).apply()
    }
    fun forgetToken() { prefs.edit().remove("token").remove("iv").apply() }
    private fun token(): String? {
        val encrypted = prefs.getString("token", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)))
            }
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)))
        }.getOrElse { error("Reconecte o GitHub nas configurações.") }
    }
    private fun api(path: String, accept: String): HttpsURLConnection {
        val connection = URL("https://api.github.com/repos/${UpdatePolicy.REPOSITORY}/$path").openConnection() as HttpsURLConnection
        connection.connectTimeout = 15000; connection.readTimeout = 30000; connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "RiseEdit-Android")
        token()?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        return connection
    }
    fun latest(): AppRelease? {
        val connection = api("releases/latest", "application/vnd.github+json")
        try {
            when (connection.responseCode) {
                404 -> error("Nenhuma versão publicada ou acesso privado indisponível. Verifique a conexão com GitHub.")
                401, 403 -> error("Acesso GitHub recusado. Use token com Contents: Read-only apenas para rise-edit.")
            }
            check(connection.responseCode == 200) { "GitHub indisponível (${connection.responseCode})." }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
            val tag = release.getString("tag_name")
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            if (!UpdatePolicy.newer(tag, installed)) return null
            val assets = release.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.getString("name") == UpdatePolicy.ASSET_NAME }
                ?: error("A versão não possui rise-edit.apk.")
            val digest = UpdatePolicy.digest(asset.optString("digest")) ?: error("APK sem SHA-256 verificável no GitHub.")
            val size = asset.getLong("size")
            require(size in 1..300_000_000) { "Tamanho da atualização inválido." }
            return AppRelease(tag, release.optString("body").take(4000), asset.getLong("id"), size, digest)
        } finally { connection.disconnect() }
    }
    suspend fun download(release: AppRelease, progress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "rise-edit.apk")
        val initial = api("releases/assets/${release.assetId}", "application/octet-stream")
        var connection = initial
        try {
            if (initial.responseCode in listOf(301, 302, 303, 307)) {
                val url = URL(initial.getHeaderField("Location") ?: error("Download sem destino"))
                require(url.protocol == "https" && (url.host == "release-assets.githubusercontent.com" || url.host.endsWith(".githubusercontent.com"))) { "Destino do download não é válido." }
                initial.disconnect()
                connection = (url.openConnection() as HttpsURLConnection).apply {
                    connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = false
                    // Do not forward the GitHub credential to the asset host.
                }
            }
            check(connection.responseCode == 200) { "Download recusado (${connection.responseCode})." }
            val hash = MessageDigest.getInstance("SHA-256")
            var count = 0L
            connection.inputStream.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    count += n; check(count <= release.size) { "APK maior que o anunciado." }
                    output.write(buffer, 0, n); hash.update(buffer, 0, n); progress(count.toFloat() / release.size)
                }
            } }
            check(count == release.size && hash.digest().joinToString("") { "%02x".format(it) } == release.sha256) { "Falha ao verificar a integridade do APK." }
            validate(file)
            return file
        } catch (e: Exception) { file.delete(); throw e }
        finally { connection.disconnect(); initial.disconnect() }
    }
    @Suppress("DEPRECATION")
    private fun validate(file: File) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("APK inválido.")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        check(candidate.packageName == context.packageName) { "O APK pertence a outro aplicativo." }
        val newCode = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        val oldCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        check(newCode > oldCode) { "A atualização não possui um versionCode maior." }
        val a = if (Build.VERSION.SDK_INT >= 28) candidate.signingInfo?.apkContentsSigners else candidate.signatures
        val b = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        check(!a.isNullOrEmpty() && !b.isNullOrEmpty() && a.toSet() == b.toSet()) { "A assinatura do APK não corresponde à versão instalada." }
    }
    fun installationIntent(file: File): Intent {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        }
        return Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.files", file), "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
