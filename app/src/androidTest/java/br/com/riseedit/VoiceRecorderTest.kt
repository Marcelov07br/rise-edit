package br.com.riseedit

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.tools.VoiceRecorder
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Regression for the Áudio → Gravar crash: every MediaRecorder path must end released and never throw. */
class VoiceRecorderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val directory = File(context.cacheDir, "recorder-test").apply { deleteRecursively() }

    @Before
    fun grant() {
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECORD_AUDIO
        )
    }

    private fun recorder() = instrumentation.runOnMainSyncResult {
        VoiceRecorder(context, directory)
    }

    @Test
    fun repeatedRecordingsProduceFilesAndReleaseCleanly() {
        val voice = recorder()
        repeat(3) {
            assertNull(voice.start())
            assertTrue(voice.recording)
            Thread.sleep(1_200)
            val result = voice.stop()
            assertTrue("Recording $it: $result", result is VoiceRecorder.Result.Saved)
            assertTrue((result as VoiceRecorder.Result.Saved).file.length() > 0)
            assertFalse(voice.recording)
        }
    }

    @Test
    fun stopRightAfterStartIsReportedNotThrown() {
        val voice = recorder()
        repeat(3) {
            assertNull(voice.start())
            val result = voice.stop()
            assertFalse(voice.recording)
            if (result is VoiceRecorder.Result.Failed) {
                assertTrue(
                    directory.listFiles().orEmpty().none { f ->
                        f.length() ==
                            0L
                    }
                )
            }
        }
    }

    @Test
    fun releaseIsIdempotentAndCoversPanelCloseWhileRecording() {
        val voice = recorder()
        assertNull(voice.release())
        assertNull(voice.start())
        assertNull("Second start while recording is ignored", voice.start())
        Thread.sleep(1_100)
        assertNotNull(voice.release())
        assertNull(voice.release())
        assertTrue(voice.stop() is VoiceRecorder.Result.Failed)
    }

    @Test
    fun prepareFailureReleasesRecorderAndReturnsMessage() {
        val unwritable = File("/proc/rise-edit-not-writable")
        val voice = VoiceRecorder(context, unwritable)
        val error = voice.start()
        assertNotNull(error)
        assertFalse(voice.recording)
        assertNull(voice.release())
    }

    private fun <T> android.app.Instrumentation.runOnMainSyncResult(block: () -> T): T {
        var value: T? = null
        runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
