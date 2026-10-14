package br.com.riseedit

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import br.com.riseedit.editor.ExportBus
import br.com.riseedit.editor.ExportService
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Background export: editor stays free; cancel cleans temp files; a later export succeeds. */
class EditorOperationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ui = ToolTestUi(instrumentation)

    @Test
    fun backgroundExportAllowsEditingAndCancelThenCompletesAnother() {
        val source = File.createTempFile("operation-source-", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("compatibility/h264_720p_scrub.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val digest = hash(source)
        val clips = (0 until 4).map {
            Clip(uri = Uri.fromFile(source).toString(), name = "Scene $it", sourceDurationMs = 3000)
        }
        val project = Project(clips, name = "Operation ownership")
        val cancelled = File.createTempFile("operation-cancelled-", ".mp4", context.cacheDir)
        val complete = File.createTempFile("operation-complete-", ".mp4", context.cacheDir)
        var activity: MainActivity? = null
        try {
            activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ) as MainActivity
            val host = activity
            val vm = ui.onMain { ViewModelProvider(host).get(EditorViewModel::class.java) }
            ui.onMain { vm.open(project) }
            ui.await { ui.onMain { vm.state.value.busy == null } }
            cancelBackgroundExport(vm, cancelled)
            ui.onMain { vm.export(Uri.fromFile(complete)) }
            ui.await(90000) {
                ui.onMain {
                    !ExportBus.state.value.active &&
                        vm.state.value.message?.startsWith("Vídeo exportado.") == true
                }
            }
            verifyExport(complete)
            assertArrayEquals(digest, hash(source))
        } finally {
            activity?.let { host -> ui.onMain { host.finish() } }
            ProjectStore(context).delete(project.id)
            listOf(source, cancelled, complete).forEach { it.delete() }
        }
    }

    private fun cancelBackgroundExport(vm: EditorViewModel, destination: File) {
        val initial = temporaryExports()
        ui.onMain { vm.export(Uri.fromFile(destination)) }
        ui.await(30000) {
            ui.onMain {
                val rendering = ExportBus.state.value.active &&
                    (temporaryExports() - initial).isNotEmpty()
                if (rendering) {
                    // Editor stays free while the service owns the encode.
                    assertEquals(null, vm.state.value.busy)
                    assertTrue(vm.trim(1, 2999))
                    ExportService.cancel(context)
                }
                rendering
            }
        }
        ui.await(30000) { ui.onMain { !ExportBus.state.value.active } }
        assertTrue(
            ui.onMain {
                vm.state.value.message?.contains("cancelad", ignoreCase = true) == true
            }
        )
        assertTrue("Temporary export survived cleanup", (temporaryExports() - initial).isEmpty())
        assertEquals(0L, destination.length())
    }

    private fun verifyExport(file: File) {
        assertTrue(file.length() > 5000)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )!!.toLong()
            assertTrue(duration in 11000..13000)
            assertNotNull(retriever.getFrameAtTime(1000000))
        } finally {
            retriever.release()
        }
    }

    private fun temporaryExports(): Set<String> = context.cacheDir.listFiles().orEmpty()
        .filter { it.name.startsWith("export-") && it.extension == "mp4" }.map { it.name }.toSet()

    private fun hash(file: File): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())
}
