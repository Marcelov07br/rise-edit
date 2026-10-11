@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

val RiseGreen = Color(0xFFBEFF68)
val RiseBackground = Color(0xFF101116)
val RiseSurface = Color(0xFF1B1D25)
fun time(ms: Long): String = String.format(Locale.US,"%02d:%02d.%01d",ms/60000,(ms/1000)%60,(ms%1000)/100)

class MainActivity : ComponentActivity() {
    private val editor by viewModels<EditorViewModel>()
    private val updates by viewModels<UpdateViewModel>()
    private val library by viewModels<ProjectLibrary>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary=RiseGreen,onPrimary=Color(0xFF173000),background=RiseBackground,surface=RiseSurface,secondary=Color(0xFFB7ABFF))) {
                val state by editor.state.collectAsStateWithLifecycle()
                val update by updates.state.collectAsStateWithLifecycle()
                val projects by library.projects.collectAsStateWithLifecycle()
                val libraryError by library.message.collectAsStateWithLifecycle()
                var editing by rememberSaveable { mutableStateOf(false) }
                var settings by remember { mutableStateOf(false) }
                fun home() { if (state.busy == null) { editor.player.stop(); editing=false; library.refresh() } }
                BackHandler(enabled=editing && state.busy == null) { home() }
                Surface(Modifier.fillMaxSize()) {
                    if (editing) EditorScreen(editor,state,onBack={ home() },onSettings={ settings=true })
                    else HomeScreen(projects,libraryError,library,onOpen={ editor.open(it); editing=true },onSettings={ settings=true },hasUpdate=update.release != null)
                }
                if (settings) UpdateSettings(updates,update,state.busy == null,install={ updates.installationIntent()?.let { intent ->
                    runCatching { startActivity(intent) }.onFailure { editor.message("Não foi possível abrir o instalador.") }
                } },close={ settings=false })
            }
        }
    }
    override fun onStart() { super.onStart(); updates.onOpen() }
    override fun onStop() { editor.player.pause(); super.onStop() }
}
