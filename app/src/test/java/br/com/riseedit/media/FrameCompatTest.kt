package br.com.riseedit.media

import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import br.com.riseedit.composition.ProjectVideoCompositorProbe
import br.com.riseedit.diagnostics.ColorDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCompatTest {
    @Test fun detectsEmptyQueueInCauseChain() {
        val wrapped =
            RuntimeException("7001", VideoFrameProcessingException(NoSuchElementException()))
        assertTrue(FrameCompat.isFrameRegistrationFailure(wrapped))
        assertFalse(FrameCompat.isFrameRegistrationFailure(VideoFrameProcessingException("gl")))
        assertFalse(FrameCompat.isFrameRegistrationFailure(null))
    }

    @Test fun rootCauseIsTheDeepest() {
        val root = NoSuchElementException()
        assertEquals(
            root,
            ColorDiagnostics.rootCause(RuntimeException(IllegalStateException(root)))
        )
    }

    @Test fun bitDepthFromProfile() {
        assertEquals(8, ColorDiagnostics.bitDepth("video/avc", 8))
        assertEquals(10, ColorDiagnostics.bitDepth("video/avc", 0x10))
        assertEquals(10, ColorDiagnostics.bitDepth("video/hevc", 2))
        assertNull(ColorDiagnostics.bitDepth("video/avc", null))
    }

    @Test fun compositorSurvivesEmptySizes() {
        assertEquals(Size(1920, 1080), ProjectVideoCompositorProbe.outputSize(emptyList()))
        assertEquals(Size(640, 360), ProjectVideoCompositorProbe.outputSize(listOf(Size(640, 360))))
    }
}
