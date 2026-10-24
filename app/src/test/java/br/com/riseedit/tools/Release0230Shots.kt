package br.com.riseedit.tools

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.EditorState
import br.com.riseedit.HomeActions
import br.com.riseedit.HomeScreen
import br.com.riseedit.ProjectStore
import br.com.riseedit.RiseTheme
import br.com.riseedit.core.Clip
import br.com.riseedit.core.MotionGoal
import br.com.riseedit.core.Project
import br.com.riseedit.core.Stabilization
import br.com.riseedit.core.StabilizeMethod
import br.com.riseedit.designsystem.RiseScreenFrame
import br.com.riseedit.media.UprightFrames
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** 0.23.0: stabilizer toggle states + independent Força do Warp, remembered library, rotation. */
class Release0230Shots {
    @get:Rule
    val p = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    // Intensidade 40 % and Força do Warp 90 %: independent values.
    private fun s(on: Boolean) = Stabilization(on, MotionGoal.SMOOTH, 0.40, 0.6, 1.10, StabilizeMethod.WARP, 0.6, 0.90)

    private fun state(on: Boolean): EditorState {
        val clip = Clip(uri = "file:///praia.mp4", name = "Praia", sourceDurationMs = 8000).copy(stabilization = s(on))
        return EditorState(project = Project(clips = listOf(clip)), analyzed = setOf(clip.uri))
    }

    private fun shot(on: Boolean, tab: StabilizeTab) = p.snapshot {
        RiseTheme {
            RiseScreenFrame {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Brush.verticalGradient(listOf(Color(0xFF5AA0D8), Color(0xFFE9C58A)))))
                    Column(Modifier.padding(16.dp)) { StabilizeContent(state(on), StabilizeActions(hasClipboard = true), tab) }
                }
            }
        }
    }

    @Test fun stabilizeOnIntensidade() = shot(true, StabilizeTab.SMOOTH)
    @Test fun stabilizeOffIntensidade() = shot(false, StabilizeTab.SMOOTH)
    @Test fun stabilizeOnForcaWarp() = shot(true, StabilizeTab.WARP)
    @Test fun stabilizeOffForcaWarp() = shot(false, StabilizeTab.WARP)

    @Test fun homeRememberedLibrary() = p.snapshot {
        Shots0221.Frame { HomeScreen(Shots0221.projects, null, Shots0221.libs, Shots0221.libs.resolveSelected("cli"), HomeActions()) }
    }
    @Test fun homeRememberedLibraryDeleted() = p.snapshot {
        Shots0221.Frame { HomeScreen(Shots0221.projects, null, Shots0221.libs, Shots0221.libs.resolveSelected("apagada"), HomeActions()) }
    }

    private fun store(): Pair<ProjectStore, File> {
        val dir = java.nio.file.Files.createTempDirectory("store0230").toFile()
        val ctx = object : android.content.ContextWrapper(p.context) {
            override fun getFilesDir() = dir
            val prefs = MemPrefs()
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = prefs
        }
        return ProjectStore(ctx) to dir
    }

    /** Old projects (no "warp" key) inherit the shared strength; new ones keep both values. */
    @Test fun warpMigration() {
        val (store, dir) = store()
        val clip = Clip(uri = "file:///a.mp4", name = "a", sourceDurationMs = 1000).copy(stabilization = s(true))
        val project = Project(clips = listOf(clip))
        store.save(project)
        val back = store.load(project.id).clips[0].stabilization
        assertEquals(0.40, back.strength, 1e-9); assertEquals(0.90, back.warpStrength, 1e-9)
        val f = File(dir, "projects/${project.id}.json")
        val legacy = org.json.JSONObject(f.readText())
        legacy.getJSONArray("clips").getJSONObject(0).getJSONObject("stabilization").remove("warp")
        f.writeText(legacy.toString())
        val old = store.load(project.id).clips[0].stabilization
        assertEquals(0.40, old.strength, 1e-9); assertEquals(0.40, old.warpStrength, 1e-9)
    }

    /** Real Bitmap rotation (layoutlib): marked top-left pixel lands where FrameRotation says. */
    @Test fun uprightRotation90And270() {
        for ((deg, expect) in listOf(90 to (1 to 0), 270 to (0 to 3))) {
            val b = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK); setPixel(0, 0, android.graphics.Color.RED) }
            val r = UprightFrames.rotate(b, deg)
            assertEquals(2, r.width); assertEquals(4, r.height)
            assertEquals(android.graphics.Color.RED, r.getPixel(expect.first, expect.second))
        }
    }
}
