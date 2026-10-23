package br.com.riseedit.tools

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.HomeActions
import br.com.riseedit.HomeScreen
import br.com.riseedit.NameDialogBody
import br.com.riseedit.NewProjectForm
import br.com.riseedit.NewProjectScreen
import br.com.riseedit.RiseTheme
import br.com.riseedit.core.Aspect
import br.com.riseedit.core.FrameRateChoice
import br.com.riseedit.core.Project
import br.com.riseedit.core.Resolution
import br.com.riseedit.core.TvStandard
import br.com.riseedit.core.library.ProjectLibraries
import br.com.riseedit.designsystem.LocalRiseInsets
import br.com.riseedit.designsystem.RiseScreenFrame
import br.com.riseedit.picker.GalleryAccess
import br.com.riseedit.picker.GalleryItem
import br.com.riseedit.picker.GalleryTab
import br.com.riseedit.picker.MediaPickerScreen
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/** 0.22.1 shared fixtures: Home with bibliotecas, Criar projeto, picker, rename dialog. */
object Shots0221 {
    private val t0 = 1_792_000_000_000L
    val projects = listOf("Praia ao pôr do sol", "Vlog Lisboa", "Skate parque", "Casamento Ana", "Cliente — teaser", "Trilha serra", "Show sábado")
        .mapIndexed { i, n -> Project(id = "p$i", name = n, modifiedAt = t0 - i * 3_600_000L, favorite = i % 3 == 0) }
    val libs = ProjectLibraries().normalized(projects.map { it.id })
        .create("Clientes", "cli").create("Viagens", "via")
        .move("p4", "cli").move("p3", "cli").move("p1", "via")
    val form = NewProjectForm("Projeto 8", Aspect.entries.first(), Resolution.entries.first(), TvStandard.entries.first(), FrameRateChoice(30, false))
    val items = (1..24).map { GalleryItem(it.toLong(), Uri.parse("content://m/$it"), true, it * 9_000L, "v$it") }

    @Composable fun Frame(content: @Composable () -> Unit) = RiseTheme {
        CompositionLocalProvider(LocalRiseInsets provides WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) { RiseScreenFrame { content() } }
    }
    @Composable fun Home() = HomeScreen(projects, null, libs, ProjectLibraries.DEFAULT_ID, HomeActions())
    @Composable fun NewProject() = NewProjectScreen(form, {}, {}, {}, libraryName = "Padrão")
    @Composable fun Picker() = MediaPickerScreen(GalleryTab.VIDEOS, items, listOf(items[2].uri, items[5].uri), GalleryAccess.FULL, 3, {}, {}, {}, {}, {}) {
        val c = Color(0xFF2E5A88 + (it.id % 6) * 0x0B0904)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c, Color.Black))))
    }
    @Composable fun RenameDialog() = Box(Modifier.fillMaxSize()) {
        Home()
        Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
            NameDialogBody("Renomear biblioteca", "Nome da biblioteca", "Padrão", "Salvar", {}, {})
        }
    }
}

class Release0221PhoneShots {
    @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")
    @Test fun homePhone() = p.snapshot { Shots0221.Frame { Shots0221.Home() } }
    @Test fun newProjectPhone() = p.snapshot { Shots0221.Frame { Shots0221.NewProject() } }
    @Test fun pickerPhone() = p.snapshot { Shots0221.Frame { Shots0221.Picker() } }
    @Test fun renameLibraryPhone() = p.snapshot { Shots0221.Frame { Shots0221.RenameDialog() } }
}

class Release0221DexShots {
    /** Marce's DeX window: ~1024×576 dp landscape (1536×864 px at 240 dpi). */
    private val dex = DeviceConfig.PIXEL_C.copy(
        screenWidth = 1536, screenHeight = 864, xdpi = 240, ydpi = 240,
        density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE
    )
    @get:Rule val p = Paparazzi(deviceConfig = dex, theme = "android:Theme.Material.NoActionBar")
    @Test fun homeDex() = p.snapshot { Shots0221.Frame { Shots0221.Home() } }
    @Test fun newProjectDex() = p.snapshot { Shots0221.Frame { Shots0221.NewProject() } }
    @Test fun pickerDex() = p.snapshot { Shots0221.Frame { Shots0221.Picker() } }
    @Test fun renameLibraryDex() = p.snapshot { Shots0221.Frame { Shots0221.RenameDialog() } }
}
