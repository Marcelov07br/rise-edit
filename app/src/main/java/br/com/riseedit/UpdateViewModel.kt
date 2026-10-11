package br.com.riseedit

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class UpdateState(val automatic: Boolean = true, val connected: Boolean = false, val busy: Boolean = false,
    val message: String = "As novas versões são recebidas pelo GitHub.", val release: AppRelease? = null,
    val progress: Float = 0f, val ready: File? = null, val installedVersion: String = "")

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = UpdateManager(application)
    private val installedVersion = application.packageManager.getPackageInfo(application.packageName, 0).versionName.orEmpty()
    private val mutable = MutableStateFlow(UpdateState(automatic = manager.automatic, connected = manager.hasToken, installedVersion = installedVersion))
    val state = mutable.asStateFlow()
    private var lastCheckAt: Long? = null
    fun onOpen() {
        val last = lastCheckAt
        if (manager.automatic && state.value.ready == null &&
            (last == null || SystemClock.elapsedRealtime() - last >= 6 * 60 * 60 * 1000L)) check()
    }
    fun automatic(value: Boolean) { manager.automatic = value; mutable.value = state.value.copy(automatic = value) }
    fun connect(token: String) {
        if (state.value.busy) return
        runCatching { require(token.isNotBlank()); manager.saveToken(token) }.onSuccess {
            mutable.value = state.value.copy(connected = true, message = "Credencial salva somente neste aparelho."); check()
        }.onFailure { mutable.value = state.value.copy(message = "Não foi possível guardar a credencial.") }
    }
    fun disconnect() { if (!state.value.busy) { manager.forgetToken(); lastCheckAt = null; mutable.value = UpdateState(automatic = manager.automatic, installedVersion = installedVersion) } }
    fun check() {
        if (state.value.busy) return
        lastCheckAt = SystemClock.elapsedRealtime()
        mutable.value = state.value.copy(busy = true, message = "Buscando versões no GitHub…", ready = null)
        viewModelScope.launch {
            try {
                val release = withContext(Dispatchers.IO) { manager.latest() }
                mutable.value = state.value.copy(release = release, message = release?.let { "Versão ${it.tag} disponível." } ?: "Você está na versão mais recente publicada.")
            } catch (e: Exception) { mutable.value = state.value.copy(message = e.localizedMessage ?: "Falha ao consultar GitHub.") }
            finally { mutable.value = state.value.copy(busy = false) }
        }
    }
    fun download() {
        val release = state.value.release ?: return
        if (state.value.busy) return
        mutable.value = state.value.copy(busy = true, progress = 0f, message = "Baixando e verificando atualização…")
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { manager.download(release) { mutable.value = mutable.value.copy(progress = it) } }
                mutable.value = state.value.copy(ready = file, message = "APK verificado. Toque em instalar; o Android pedirá confirmação.")
            } catch (e: Exception) { mutable.value = state.value.copy(message = e.localizedMessage ?: "Falha no download.") }
            finally { mutable.value = state.value.copy(busy = false) }
        }
    }
    fun installationIntent(): Intent? = state.value.ready?.let { manager.installationIntent(it) }
}
