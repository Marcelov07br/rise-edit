package br.com.riseedit.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.riseedit.RisePalette
import br.com.riseedit.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** 0.19: single source of app preferences, persisted in DataStore (Preferences). */
object SettingsStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = mutable.asStateFlow()
    val current: AppSettings get() = mutable.value
    private var context: Context? = null

    /** Reads once synchronously (small file) so the first frame already has the right theme. */
    fun install(app: Context) {
        context = app.applicationContext
        val store = app.applicationContext.settingsStore
        val first = runCatching {
            runBlocking {
                store.data.first()
            }.asMap().mapKeys { it.key.name }.mapValues { it.value.toString() }
        }.getOrDefault(emptyMap())
        apply(AppSettings.fromMap(first))
    }

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(mutable.value)
        apply(next)
        val store = context?.settingsStore ?: return
        scope.launch {
            store.edit { prefs ->
                prefs.clear()
                next.toMap().forEach { (k, v) -> prefs[stringPreferencesKey(k)] = v }
            }
        }
    }

    private fun apply(s: AppSettings) {
        mutable.value = s
        RisePalette.accent = s.accent
        RisePalette.haptics = s.haptics
        RisePalette.reduceMotion = s.reduceMotion
        if (s.theme != ThemeMode.SYSTEM) RisePalette.dark = s.theme == ThemeMode.DARK
    }
}
