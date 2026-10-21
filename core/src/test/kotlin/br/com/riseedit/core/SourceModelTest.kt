package br.com.riseedit.core

import org.junit.Assert.*
import org.junit.Test

class SourceModelTest {
    private fun clip() = Clip(uri = "content://original", name = "Vídeo", sourceDurationMs = 10_000)

    @Test fun additiveClipDefaultsKeepExistingProjectsOnOriginalMedia() {
        val clip = clip()
        assertNull(clip.sourceInfo)
        assertNull(clip.proxyUri)
        assertFalse(clip.proxyRequired)
        assertEquals("content://original", clip.effectiveUri())
        assertEquals("content://original", clip.effectiveUri(preferProxy = true))
    }

    @Test fun effectiveUriHonorsPreviewPreferenceAndRequiredProxy() {
        val clip = clip().copy(proxyUri = "file://proxy")
        assertEquals("content://original", clip.effectiveUri(preferProxy = false))
        assertEquals("file://proxy", clip.effectiveUri(preferProxy = true))
        assertEquals("file://proxy", clip.copy(proxyRequired = true).effectiveUri(preferProxy = false))
        assertEquals("content://original", clip.copy(proxyUri = " ", proxyRequired = true).effectiveUri())
    }

    @Test fun metadataDefaultsRepresentUnknownValuesExplicitly() {
        val info = SourceInfo("video/hevc", 3840, 2160)
        assertEquals(0, info.profile)
        assertEquals(0, info.level)
        assertEquals(ColorDepth.UNKNOWN, info.bitDepth)
        assertEquals(0, info.bitDepth.bits)
        assertEquals(listOf(8, 10, 12), listOf(ColorDepth.EIGHT, ColorDepth.TEN, ColorDepth.TWELVE).map { it.bits })
        assertEquals(ChromaFormat.UNKNOWN, info.chroma)
        assertFalse(info.isHdr)
        assertEquals(0f, info.frameRate, 0f)
        assertNull(info.audioMime)
        assertEquals(0, info.audioChannels)
        assertEquals(0, info.audioSampleRate)
    }

    @Test fun splitsAndTrimsRetainMetadataProxiesAndEditingControls() {
        val info = SourceInfo("video/avc", 1920, 1080)
        val clip = clip().copy(
            sourceInfo = info, proxyUri = "file://proxy", proxyRequired = true,
            speedPoints = listOf(SpeedPoint(0, 2.0)),
            adjustments = ColorAdjust(brightness = 0.2f),
            keyframes = listOf(TransformKeyframe(0, x = 0.25f))
        )
        (clip.split(1000) + clip.trim(2000, 6000)).forEach { part ->
            assertEquals(info, part.sourceInfo)
            assertEquals("file://proxy", part.effectiveUri())
            assertTrue(part.proxyRequired)
            assertEquals(clip.speedPoints, part.speedPoints)
            assertEquals(clip.adjustments, part.adjustments)
            assertEquals(clip.keyframes, part.keyframes)
        }
        assertEquals(clip.durationMs, clip.split(1000).sumOf { it.durationMs })
    }

    @Test fun mediaFailuresGivePortugueseMessagesAndActionsWithoutLeakingDetails() {
        val cause = IllegalStateException("private/path/decoder stack")
        val faults = listOf(
            MediaFault.MissingDecoder("video/apv"), MediaFault.UnsupportedApv,
            MediaFault.UnsupportedChroma(ChromaFormat.YUV422), MediaFault.Unsupported10Bit,
            MediaFault.ProxyFailed(cause = cause), MediaFault.ImportFailed(cause = cause),
            MediaFault.AssetUnavailable("private/path", cause), MediaFault.ExportFailed("private/path", cause),
            MediaFault.AssetDecodeFailed(cause), MediaFault.StabilizationNoTexture, MediaFault.ExportCancelled
        )
        faults.forEach { fault ->
            assertTrue(fault.userMessage.isNotBlank())
            assertTrue(fault.suggestedAction.isNotBlank())
            assertEquals(fault.userMessage, fault.message)
            assertFalse(fault.userMessage.contains("private/path"))
            assertFalse(fault.suggestedAction.contains("private/path"))
        }
        assertSame(cause, MediaFault.ProxyFailed(cause = cause).cause)
    }

    @Test fun proxyAndImportFailuresExposeTrustedPortugueseDetails() {
        val encoderDetail = "Este aparelho não oferece um codificador H.264."
        assertTrue(MediaFault.ProxyFailed(encoderDetail).userMessage.endsWith(encoderDetail))
        val importDetail = "O arquivo não contém vídeo."
        assertTrue(MediaFault.ImportFailed(importDetail).userMessage.endsWith(importDetail))
        assertEquals(MediaFault.ProxyFailed().userMessage, MediaFault.ProxyFailed(" ").userMessage)
    }
}
