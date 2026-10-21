package br.com.riseedit.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import br.com.riseedit.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App version from the package (no BuildConfig in this project). */
fun appVersion(context: Context): Pair<String, Long> = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    (info.versionName ?: "?") to info.longVersionCode
}.getOrDefault("?" to 0L)

/** Cache = cacheDir; proxies still referenced by a saved project are kept. */
internal object CacheCleaner {
    private fun inUse(context: Context): Set<String> = runCatching {
        ProjectStore(context).list().flatMap { p ->
            (p.clips + p.overlays.map { it.clip }).mapNotNull { it.proxyUri }
        }
            .mapNotNull { Uri.parse(it).path }.toSet()
    }.getOrDefault(emptySet())

    fun size(context: Context): Long = context.cacheDir.walkBottomUp().filter {
        it.isFile
    }.sumOf { it.length() }

    fun clear(context: Context): Long {
        val keep = inUse(context)
        context.cacheDir.walkBottomUp().filter {
            it.isFile && it.absolutePath !in keep
        }.forEach { it.delete() }
        context.cacheDir.walkBottomUp().filter { it.isDirectory && it != context.cacheDir }
            .forEach { if (it.listFiles().isNullOrEmpty()) it.delete() }
        return size(context)
    }
}

/** The Configurações route: state + system intents around the stateless [SettingsScreen]. */
@Suppress("LongMethod") // single Compose screen body
@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsStore.state.collectAsState()
    var section by rememberSaveable { mutableStateOf(SettingsSection.UPDATES) }
    var cache by remember { mutableLongStateOf(0L) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) { cache = withContext(Dispatchers.IO) { CacheCleaner.size(context) } }
    val folder =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            SettingsStore.update { it.copy(exportFolder = uri.toString()) }
        }
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(clearing) {
        if (clearing) {
            cache = withContext(Dispatchers.IO) { CacheCleaner.clear(context) }
            clearing = false
        }
    }
    val (name, code) = remember { appVersion(context) }
    SettingsScreen(
        settings,
        section,
        { section = it },
        SettingsStore::update,
        SettingsInfo(name, code, cache, settings.exportFolder?.let { folderLabel(it) }),
        SettingsActions(
            back = onBack,
            openReleases = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(RELEASES_URL)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            pickFolder = { folder.launch(null) },
            clearCache = {
                clearing = true
                refresh++
            },
            shareLogs = {
                runCatching {
                    context.startActivity(
                        br.com.riseedit.diagnostics.Diagnostics.shareIntent(
                            context,
                            "$name ($code)"
                        )
                    )
                }
            }
        )
    )
}

/** "primary:Movies/Rise" → "Movies/Rise". */
internal fun folderLabel(tree: String): String =
    Uri.decode(tree.substringAfterLast("/tree/", tree)).substringAfter(":").ifBlank {
        "Armazenamento"
    }
