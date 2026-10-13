package br.com.riseedit

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the real ViewModel owner across cancellation, cleanup and a subsequent export. */
class EditorOperationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val ui = ToolTestUi(instrumentation)

    @Test
    fun cancelledExportKeepsOwnerUntilCleanupThenAllowsAnotherExport() {
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
            cancelAndVerifyOwner(vm, cancelled, complete)
            ui.onMain { vm.export(Uri.fromFile(complete)) }
            ui.await(90000) { ui.onMain { vm.state.value.busy == null } }
            assertTrue(ui.onMain { vm.state.value.message?.startsWith("Vídeo exportado.") == true })
            verifyExport(complete)
            assertArrayEquals(digest, hash(source))
        } finally {
            activity?.let { host -> ui.onMain { host.finish() } }
            ProjectStore(context).delete(project.id)
            listOf(source, cancelled, complete).forEach { it.delete() }
        }
    }

    private fun cancelAndVerifyOwner(vm: EditorViewModel, destination: File, other: File) {
        val initial = temporaryExports()
        ui.onMain { vm.export(Uri.fromFile(destination)) }
        ui.await(30000) {
            ui.onMain {
                val rendering = vm.state.value.busy?.startsWith("Exportando MP4") == true &&
                    (temporaryExports() - initial).isNotEmpty()
                if (rendering) {
                    // Observe and cancel in the same main-thread action, before completion can race us.
                    assertNotNull(vm.state.value.busy)
                    vm.cancel()
                    if (vm.state.value.busy != null) {
                        vm.trim(1, 2999)
                        vm.export(Uri.fromFile(other))
                        assertEquals(0L, vm.state.value.project.clips.first().startMs)
                    } else {
                        // Synchronous cleanup may release ownership before cancel returns.
                        assertTrue(
                            "Owner released before cleanup",
                            (temporaryExports() - initial).isEmpty()
                        )
                    }
                }
                rendering
            }
        }
        ui.await(30000) { ui.onMain { vm.state.value.busy == null } }
        assertEquals("Operação cancelada.", ui.onMain { vm.state.value.message })
        assertTrue("Temporary export survived cleanup", (temporaryExports() - initial).isEmpty())
        assertEquals(0L, other.length())
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
