package br.com.riseedit.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.ProjectStore
import br.com.riseedit.RiseTheme
import br.com.riseedit.core.perf.PerfFixture
import br.com.riseedit.designsystem.LocalRiseInsets
import br.com.riseedit.timeline.TimelineActions
import br.com.riseedit.timeline.TimelinePerfProbe
import br.com.riseedit.timeline.TimelineStage
import br.com.riseedit.timeline.TimelineUiState
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Perf 0.22.2 (JVM/layoutlib, NOT a device): composes the real TimelineStage with the 45-min,
 * 2000-cut fixture on a DeX-sized window and counts clip nodes per composition / per frame.
 */
class TimelinePerfShots {
    private val dex = DeviceConfig.PIXEL_C.copy(
        screenWidth = 1920, screenHeight = 1080, xdpi = 240, ydpi = 240,
        density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE
    )
    @get:Rule val p = Paparazzi(deviceConfig = dex, theme = "android:Theme.Material.NoActionBar")
    private val project = PerfFixture.marce()
    private val out = StringBuilder()

    private fun log(s: String) { println("PERF $s"); out.appendLine(s) }

    /** Test-only: the stage never calls the VM unless the user acts, so skip its constructor. */
    private fun fakeVm(): EditorViewModel {
        val f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
        val u = f.get(null)
        return u.javaClass.getMethod("allocateInstance", Class::class.java).invoke(u, EditorViewModel::class.java) as EditorViewModel
    }

    private fun scenario(name: String, frames: Int, zoomMin: Boolean, perFrame: ((TimelineUiState, androidx.compose.runtime.MutableState<EditorState>) -> Unit)?) {
        val mid = project.durationMs / 2
        val state = mutableStateOf(EditorState(project = project, positionMs = mid, selected = 1000))
        val vm = fakeVm()
        var ui: TimelineUiState? = null
        var frameNs = 0L
        var counted = 0
        TimelinePerfProbe.reset()
        val t0 = System.nanoTime()
        val body = @androidx.compose.runtime.Composable {
            RiseTheme {
                CompositionLocalProvider(LocalRiseInsets provides WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) {
                    Box(Modifier.fillMaxSize()) {
                        val density = androidx.compose.ui.platform.LocalDensity.current.density
                        val u = androidx.compose.runtime.remember {
                            TimelineUiState(density).also { s ->
                                val d = project.durationMs * 1000
                                val w = 1500.0 * density
                                var v = s.viewport.resized(w, d)
                                v = if (zoomMin) v.copy(pixelsPerSecond = v.minPixelsPerSecond) else v
                                s.viewport = v.copy(originUs = mid * 1000.0 - w / 2 * 1_000_000 / v.pixelsPerSecond).bounded(d)
                            }
                        }
                        ui = u
                        TimelineStage(u, state, TimelineActions(vm, { state.value }), 400.dp) {}
                        if (perFrame != null) {
                            LaunchedEffect(Unit) {
                                repeat(frames) {
                                    withFrameNanos { }
                                    val before = System.nanoTime()
                                    perFrame(u, state)
                                    androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
                                    frameNs += System.nanoTime() - before
                                    counted++
                                }
                            }
                        }
                    }
                }
            }
        }
        if (perFrame == null) {
            p.snapshot(name) { body() }
            val ms = (System.nanoTime() - t0) / 1e6
            log("%-28s calls=%5d emitted=%4d entryBuilds=%d snapshot=%.0f ms".format(name, TimelinePerfProbe.clipCalls, TimelinePerfProbe.clipEmitted, TimelinePerfProbe.entryBuilds, ms))
        } else {
            val firstCalls = 0
            val view = androidx.compose.ui.platform.ComposeView(p.context).apply { setContent { body() } }
            p.gif(view, name = name, start = 0, end = frames * 1000L / 60, fps = 60)
            val ms = (System.nanoTime() - t0) / 1e6
            log(
                "%-28s frames=%d calls/frame=%.0f emitted/frame=%.0f entryBuilds=%d total=%.0f ms (%.1f ms/frame incl. render)".format(
                    name, frames, TimelinePerfProbe.clipCalls.toDouble() / (frames + 1), TimelinePerfProbe.clipEmitted.toDouble() / (frames + 1),
                    TimelinePerfProbe.entryBuilds, ms, ms / (frames + 1)
                ) + firstCalls
            )
        }
    }

    @Test fun timelinePerf() {
        assumeTrue(System.getProperty("perf") == "true")
        log("fixture clips=${project.clips.size} min=${project.durationMs / 60000.0} audio=${project.audio.size} overlays=${project.overlays.size} texts=${project.texts.size}")
        scenario("zoom normal (composição)", 0, zoomMin = false, perFrame = null)
        scenario("zoom mínimo (composição)", 0, zoomMin = true, perFrame = null)
        scenario("rolar 30 quadros", 30, zoomMin = false) { u, _ ->
            u.viewport = u.viewport.panned(-40.0, project.durationMs * 1000)
        }
        scenario("agulha 30 quadros", 30, zoomMin = false) { _, s ->
            s.value = s.value.copy(positionMs = s.value.positionMs + 16)
        }
        File(System.getProperty("perf.out") ?: "build/perf-ui.txt").writeText(out.toString())
    }

    /** Writes the fixture in the real project format and times ProjectStore save/load. */
    @Test fun storePerf() {
        assumeTrue(System.getProperty("perf") == "true")
        val dir = java.nio.file.Files.createTempDirectory("perfstore").toFile()
        val ctx = object : android.content.ContextWrapper(p.context) {
            override fun getFilesDir() = dir
            val prefs = MemPrefs()
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = prefs
        }
        val store = ProjectStore(ctx)
        val fixture = File("src/test/resources/perf/marce-45min.json")
        val res = mutableListOf<String>()
        PerfBenchApp.run("ProjectStore.save (2000 clipes)", warmup = 3, runs = 9) { store.save(project) }.also { res += it.toString() }
        PerfBenchApp.run("ProjectStore.load (2000 clipes)", warmup = 3, runs = 9) { store.load(project.id) }.also { res += it.toString() }
        val saved = File(dir, "projects/${project.id}.json")
        fixture.parentFile.mkdirs()
        saved.copyTo(fixture, overwrite = true)
        res += "fixture ${fixture.path} ${fixture.length() / 1024} KB"
        File(System.getProperty("perf.out")?.let { "$it.store" } ?: "build/perf-store.txt").writeText(res.joinToString("\n"))
        res.forEach { println("PERF $it") }
    }
}
