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
import androidx.compose.runtime.Composable
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

/** 0.19: top-level routes (Configurações and Criar projeto are real screens, not dialogs). */
private enum class Route { HOME, SETTINGS, SHORTCUTS, NEW_PROJECT }

class MainActivity : ComponentActivity() {
    private val editor by viewModels<EditorViewModel>()
    private val library by viewModels<ProjectLibrary>()

    @Suppress("LongMethod", "CyclomaticComplexMethod") // route host
    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContent {
            @OptIn(androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSize = androidx.compose.material3.windowsizeclass.calculateWindowSizeClass(this)
            androidx.compose.runtime.CompositionLocalProvider(LocalWindowSizeClass provides windowSize) {
            RiseTheme {
                val state by editor.state.collectAsStateWithLifecycle()
                val projects by library.projects.collectAsStateWithLifecycle()
                val libraryLoading by library.loading.collectAsStateWithLifecycle()
                val libraryError by library.message.collectAsStateWithLifecycle()
                var activeId by rememberSaveable { mutableStateOf<String?>(null) }
                var route by rememberSaveable { mutableStateOf(Route.HOME) }
                var form by remember { mutableStateOf<NewProjectForm?>(null) }
                val editing = activeId != null
                fun home() {
                    if (state.busy == null) {
                        editor.suspendPreview()
                        editor.flushSave()
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
                BackHandler(enabled = route != Route.HOME) { route = Route.HOME }
                BackHandler(enabled = route == Route.HOME && editing && state.busy == null) {
                    home()
                }
                SystemBarIcons()
                br.com.riseedit.designsystem.RiseScreenFrame {
                  Box(Modifier.fillMaxSize()) {
                    when {
                        route == Route.SETTINGS -> br.com.riseedit.settings.SettingsRoute {
                            route =
                                Route.HOME
                        }
                        route == Route.SHORTCUTS -> br.com.riseedit.settings.SettingsRoute(
                            br.com.riseedit.settings.SettingsSection.SHORTCUTS
                        ) { route = Route.HOME }
                        route == Route.NEW_PROJECT -> {
                            val f = form ?: newForm(projects.size).also { form = it }
                            NewProjectScreen(f, { form = it }, onCreate = {
                                val d = br.com.riseedit.settings.SettingsStore.current
                                library.create(
                                    br.com.riseedit.core.ProjectFormat.newProject(
                                        f.name,
                                        f.aspect,
                                        f.resolution,
                                        f.rate,
                                        d.exportDefaults()
                                    )
                                )?.let {
                                    br.com.riseedit.picker.PickerRequest.pending = true
                                    editor.open(it)
                                    activeId = it.id
                                }
                                form = null
                                route = Route.HOME
                            }, onBack = {
                                form = null
                                route = Route.HOME
                            }, libraryName = library.libraries.value.folders
                                .firstOrNull { it.id == library.selected.value }?.name)
                        }
                        editing && state.project.id == activeId ->
                            EditorScreen(editor, state, onBack = { home() }, onSettings = {
                                route =
                                    Route.SETTINGS
                            }, onShortcuts = { route = Route.SHORTCUTS })
                        editing -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) { CircularProgressIndicator() }
                        else -> {
                            val libs by library.libraries.collectAsStateWithLifecycle()
                            val sel by library.selected.collectAsStateWithLifecycle()
                            HomeScreen(projects, libraryError, libs, sel, library.homeActions(open = {
                                editor.open(it)
                                activeId = it.id
                            }, settings = { route = Route.SETTINGS }, new = { route = Route.NEW_PROJECT }))
                        }
                    }
                    // 0.21: render tab / floating bubble above every screen.
                    br.com.riseedit.export.RenderHost()
                    br.com.riseedit.proxy.ProxyHost()
                  }
                }
            }
            }
        }
    }

    /** 0.22: the keymap in the system Meta+/ keyboard shortcuts list (DeX / hardware keyboards). */
    override fun onProvideKeyboardShortcuts(
        data: MutableList<android.view.KeyboardShortcutGroup>,
        menu: android.view.Menu?,
        deviceId: Int
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        val map = br.com.riseedit.keys.AppKeymap.get(this)
        br.com.riseedit.core.shortcuts.ShortcutGroup.entries.forEach { group ->
            val items = map.bindings.filter { it.action.group == group }.mapNotNull { b ->
                val code = br.com.riseedit.keys.AppKeymap.keyCode(b.chord) ?: return@mapNotNull null
                android.view.KeyboardShortcutInfo(
                    b.action.label,
                    code,
                    br.com.riseedit.keys.AppKeymap.metaState(b.chord)
                )
            }
            if (items.isNotEmpty()) data.add(android.view.KeyboardShortcutGroup("Rise Edit · ${group.label}", items))
        }
    }
    override fun onStop() {
        editor.pausePreview()
        editor.flushSave()
        super.onStop()
    }
}

private fun newForm(count: Int): NewProjectForm {
    val d = br.com.riseedit.settings.SettingsStore.current
    return NewProjectForm("Projeto ${count + 1}", d.aspect, d.resolution, d.standard, d.rate)
}

/** The app is always dark: status/gesture bar icons are always light. */
@Composable
private fun SystemBarIcons() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}
