@file:androidx.annotation.OptIn(
    markerClass = [
        androidx.media3.common.util.UnstableApi::class,
        androidx.media3.common.util.ExperimentalApi::class
    ]
)

package br.com.riseedit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

fun time(ms: Long): String = String.format(
    Locale.US,
    "%02d:%02d.%01d",
    ms / 60000,
    (ms / 1000) % 60,
    (ms % 1000) / 100
)

class MainActivity : ComponentActivity() {
    private val editor by viewModels<EditorViewModel>()
    private val updates by viewModels<UpdateViewModel>()
    private val library by viewModels<ProjectLibrary>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiseTheme {
                val state by editor.state.collectAsStateWithLifecycle()
                val update by updates.state.collectAsStateWithLifecycle()
                val projects by library.projects.collectAsStateWithLifecycle()
                val libraryLoading by library.loading.collectAsStateWithLifecycle()
                val libraryError by library.message.collectAsStateWithLifecycle()
                var activeId by rememberSaveable { mutableStateOf<String?>(null) }
                val editing = activeId != null
                var settings by remember { mutableStateOf(false) }
                fun home() {
                    if (state.busy ==
                        null
                    ) {
                        editor.suspendPreview()
                        activeId = null
                        library.refresh()
                    }
                }
                LaunchedEffect(activeId, projects, libraryLoading) {
                    val id = activeId
                    if (id != null && !libraryLoading && state.project.id != id) {
                        val project = projects.firstOrNull { it.id == id }
                        if (project != null) editor.open(project) else activeId = null
                    }
                }
                BackHandler(enabled = editing && state.busy == null) { home() }
                Surface(Modifier.fillMaxSize()) {
                    if (editing &&
                        state.project.id == activeId
                    ) {
                        EditorScreen(editor, state, onBack = { home() }, onSettings = {
                            settings =
                                true
                        })
                    } else if (editing) {
                        Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        HomeScreen(projects, libraryError, library, onOpen = {
                            editor.open(it)
                            activeId =
                                it.id
                        }, onSettings = { settings = true }, hasUpdate = update.release != null)
                    }
                }
                if (settings) {
                    UpdateSettings(updates, update, state.busy == null, install = {
                        updates.installationIntent()?.let { intent ->
                            runCatching {
                                startActivity(intent)
                            }.onFailure { editor.message("Não foi possível abrir o instalador.") }
                        }
                    }, close = { settings = false })
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        updates.onOpen()
    }
    override fun onStop() {
        editor.pausePreview()
        super.onStop()
    }
}
