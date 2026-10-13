package br.com.riseedit

import android.content.Intent
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineGestureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ui = ToolTestUi(instrumentation)
    private val touch = TimelineTouch(instrumentation)

    @Test
    fun pinchOnClipsAndEmptyTracksAndBothEdgesProduceAPlayableExport() {
        val source = sourceFile()
        val digest = hash(source)
        val neighborSource = File.createTempFile("timeline-neighbor-", ".mp4", context.cacheDir)
        source.copyTo(neighborSource, overwrite = true)
        val original =
            Clip(
                uri = Uri.fromFile(source).toString(),
                name = "Timeline real",
                sourceDurationMs = 3000
            )
        val neighbor = Clip(
            uri = Uri.fromFile(neighborSource).toString(),
            name = "Segunda cena",
            sourceDurationMs = 3000,
            endMs = 100
        )
        val project = Project(listOf(original, neighbor), name = "Teste de gestos")
        val store = ProjectStore(context)
        val output = File.createTempFile("timeline-export-", ".mp4", context.cacheDir)
        var activity: MainActivity? = null
        var recording: Int? = null
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
                ui.onMain {
                    vm.state.value.busy == null &&
                        vm.state.value.project.id == project.id
                }
            }
            recording = startRecording()
            verifyPinch(vm)
            verifyEdges(vm, ui.onMain { vm.state.value.project.clips.first() })
            val edited = ui.onMain { vm.state.value.project.clips.first() }
            assertEquals(neighbor.id, ui.onMain { vm.state.value.project.clips.last().id })
            verifyReopenAndPreview(vm, project.name, edited)
            ui.onMain { vm.export(Uri.fromFile(output)) }
            ui.await(90000) { ui.onMain { vm.state.value.busy == null } }
            assertTrue(ui.onMain { vm.state.value.message?.startsWith("Vídeo exportado.") == true })
            verifyExport(output, ui.onMain { vm.state.value.project.durationMs })
            assertArrayEquals(digest, hash(source))
            assertArrayEquals(digest, hash(neighborSource))
            ui.screenshot("timeline-export-complete")
        } finally {
            recording?.let { shell("kill -2 $it") }
            shell("settings put system show_touches 0")
            activity?.let { host -> ui.onMain { host.finish() } }
            store.delete(project.id)
            listOf(source, neighborSource, output).forEach { it.delete() }
        }
    }

    private fun sourceFile(): File {
        val source = File.createTempFile("timeline-source-", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("compatibility/h264_720p_scrub.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        return source
    }

    private fun startRecording(): Int? {
        shell("settings put system show_touches 1")
        val script = File(context.getExternalFilesDir(null), "record-timeline.sh")
        script.writeText(
            "#!/system/bin/sh\n" +
                "screenrecord --time-limit 120 --bit-rate 2000000 " +
                "/sdcard/Download/timeline-demo.mp4 >/dev/null 2>&1 &\n" +
                "echo \$!\n"
        )
        return shell("sh ${script.absolutePath}").trim().toIntOrNull()
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command)
            .use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { String(it.readBytes()) }
            }

    private fun verifyPinch(vm: EditorViewModel) {
        val left = bounds("Aresta inicial")
        val right = bounds("Aresta final")
        val before = right.centerX() - left.centerX()
        val center = (left.centerX() + right.centerX()) / 2f
        touch.pinch(center, left.centerY().toFloat(), before / 6f, 1.5f)
        val enlarged = bounds("Aresta final").centerX() - bounds("Aresta inicial").centerX()
        assertTrue("Pinch over the clip did not zoom", enlarged > before * 1.3)
        ui.screenshot("timeline-pinch-clip")
        val density = context.resources.displayMetrics.density
        touch.pinch(center, left.bottom + 26 * density, before / 6f, 1 / 1.5f)
        val reduced = bounds("Aresta final").centerX() - bounds("Aresta inicial").centerX()
        assertTrue("Pinch over an empty track did not zoom", reduced < enlarged * 0.8)
        assertFalse("Zoom must not create an edit", ui.onMain { vm.state.value.canUndo })
    }

    private fun verifyEdges(vm: EditorViewModel, original: Clip) {
        dragEdge("Aresta inicial", 500, original.durationMs)
        val leftTrimmed = ui.onMain { vm.state.value.project.clips.first() }
        assertTrue(leftTrimmed.startMs in 450..550)
        assertEquals(original.endMs, leftTrimmed.endMs)
        ui.onMain { vm.undo() }
        assertEquals(original, ui.onMain { vm.state.value.project.clips.first() })
        assertFalse("One drag must produce exactly one undo", ui.onMain { vm.state.value.canUndo })
        ui.onMain { vm.redo() }
        assertEquals(leftTrimmed, ui.onMain { vm.state.value.project.clips.first() })
        ui.screenshot("timeline-trim-left")
        dragEdge("Aresta final", -300, leftTrimmed.durationMs)
        val bothTrimmed = ui.onMain { vm.state.value.project.clips.first() }
        assertTrue(bothTrimmed.endMs in 2650..2750)
        assertEquals(leftTrimmed.startMs, bothTrimmed.startMs)
        ui.onMain { vm.undo() }
        assertEquals(leftTrimmed, ui.onMain { vm.state.value.project.clips.first() })
        ui.onMain { vm.redo() }
        dragEdge("Aresta final", -200, bothTrimmed.durationMs, cancel = true)
        assertEquals(
            "Cancelled gesture changed the project",
            bothTrimmed,
            ui.onMain { vm.state.value.project.clips.first() }
        )
        dragEdge("Aresta final", 300, bothTrimmed.durationMs)
        assertTrue(ui.onMain { vm.state.value.project.clips.first().endMs in 2950..3000 })
        ui.onMain { vm.undo() }
        ui.screenshot("timeline-trim-both")
    }

    private fun dragEdge(label: String, deltaMs: Long, durationMs: Long, cancel: Boolean = false) {
        val left = bounds("Aresta inicial")
        val right = bounds("Aresta final")
        val edge = if (label == "Aresta inicial") left else right
        val delta = (right.centerX() - left.centerX()) * deltaMs.toFloat() / durationMs
        touch.drag(
            edge.centerX().toFloat(),
            edge.centerY().toFloat(),
            edge.centerX() + delta,
            cancel
        )
    }

    private fun verifyReopenAndPreview(vm: EditorViewModel, name: String, clip: Clip) {
        ui.click("Projetos")
        ui.click(name, scroll = true)
        ui.await { ui.onMain { vm.state.value.busy == null } }
        assertEquals(clip, ui.onMain { vm.state.value.project.clips.first() })
        ui.onMain { vm.seek(500) }
        ui.await { ui.onMain { kotlin.math.abs(vm.previewSourceMs - clip.sourceAt(500)) <= 100 } }
        ui.screenshot("timeline-preview-trimmed")
    }

    private fun verifyExport(file: File, expectedMs: Long) {
        assertTrue(file.length() > 5000)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )!!.toLong()
            assertTrue(
                "Export duration $duration differs from $expectedMs",
                kotlin.math.abs(duration - expectedMs) < 200
            )
            assertNotNull(retriever.getFrameAtTime(500000))
        } finally {
            retriever.release()
        }
    }

    private fun bounds(label: String): Rect = Rect().also {
        ui.waitFor(label).getBoundsInScreen(it)
    }
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
}
