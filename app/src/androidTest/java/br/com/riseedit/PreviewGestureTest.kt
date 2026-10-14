package br.com.riseedit

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Direct manipulation in the preview: drag moves and pinch scales the main clip. */
class PreviewGestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ui = ToolTestUi(instrumentation)
    private val touch = TimelineTouch(instrumentation)

    @Test
    fun dragAndPinchTransformTheClipWithOneUndoEach() {
        val source = File.createTempFile("preview-gesture-", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("compatibility/h264_720p_scrub.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val clip =
            Clip(uri = Uri.fromFile(source).toString(), name = "Gesto", sourceDurationMs = 3000)
        val project = Project(listOf(clip), name = "Teste de prévia tátil")
        val store = ProjectStore(context)
        var activity: MainActivity? = null
        try {
            store.save(project)
            activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ) as MainActivity
            val host = activity
            val vm = ui.onMain { ViewModelProvider(host).get(EditorViewModel::class.java) }
            ui.click(project.name, scroll = true)
            ui.await {
                ui.onMain { vm.state.value.busy == null && vm.state.value.project.id == project.id }
            }
            val frame = Rect().also { ui.waitFor("Prévia editável").getBoundsInScreen(it) }
            val y = frame.exactCenterY()
            touch.drag(frame.exactCenterX(), y, frame.exactCenterX() + frame.width() / 4f)
            ui.await { ui.onMain { vm.state.value.project.clips.first().keyframes.isNotEmpty() } }
            val moved = ui.onMain { vm.state.value.project.clips.first().keyframes }
            assertEquals(1, moved.size)
            assertTrue("x was ${moved.first().x}", moved.first().x in 0.3f..0.7f)
            ui.screenshot("preview-drag")
            touch.pinch(frame.exactCenterX(), y, frame.width() / 8f, 1.6f)
            ui.await {
                ui.onMain {
                    vm.state.value.project.clips.first().keyframes.first().scale >
                        1.2f
                }
            }
            ui.screenshot("preview-pinch")
            ui.onMain { vm.undo() }
            assertEquals(moved, ui.onMain { vm.state.value.project.clips.first().keyframes })
            ui.onMain { vm.undo() }
            assertTrue(ui.onMain { vm.state.value.project.clips.first().keyframes.isEmpty() })
        } finally {
            activity?.let { host -> ui.onMain { host.finish() } }
            store.delete(project.id)
            source.delete()
        }
    }
}
