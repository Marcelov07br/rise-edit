package br.com.riseedit.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class CompatibilityTest {
    private val regular = SourceInfo(
        mime = "video/avc", width = 1920, height = 1080,
        bitDepth = ColorDepth.EIGHT, chroma = ChromaFormat.YUV420, frameRate = 30f
    )
    private val device = DeviceProfile(setOf("video/avc", "video/hevc"), maxDecoders = 2)

    @Test fun policyTableUsesActualFormatAndConservativeMetadata() {
        data class Case(
            val name: String,
            val source: SourceInfo,
            val decoded: Boolean = true,
            val capabilities: DeviceProfile = device,
            val direct: Boolean = false
        )
        val cases = listOf(
            Case("ordinary AVC", regular, direct = true),
            Case("ordinary HEVC", regular.copy(mime = "video/hevc"), direct = true),
            Case("actual decoder overrides broad inventory", regular, capabilities = device.copy(canDecode = emptySet()), direct = true),
            Case("actual format decoder missing", regular, decoded = false),
            Case("no available decoders", regular, capabilities = device.copy(maxDecoders = 0)),
            Case("APV", regular.copy(mime = "video/apv")),
            Case("APV alias and normalized MIME", regular.copy(mime = " VIDEO/X-APV ")),
            Case("422 remains proxy even with advertised support", regular.copy(chroma = ChromaFormat.YUV422), capabilities = device.copy(supports422 = true)),
            Case("444", regular.copy(chroma = ChromaFormat.YUV444)),
            Case("unsupported 10-bit", regular.copy(bitDepth = ColorDepth.TEN)),
            Case("supported 10-bit SDR", regular.copy(bitDepth = ColorDepth.TEN), capabilities = device.copy(supports10Bit = true), direct = true),
            Case("12-bit is not implied by 10-bit support", regular.copy(bitDepth = ColorDepth.TWELVE), capabilities = device.copy(supports10Bit = true)),
            Case("HDR needs SDR copy", regular.copy(isHdr = true), capabilities = device.copy(supports10Bit = true)),
            Case("4K landscape boundary", regular.copy(width = 3840, height = 2160), direct = true),
            Case("4K portrait boundary", regular.copy(width = 2160, height = 3840), direct = true),
            Case("above 4K width", regular.copy(width = 3842, height = 2160)),
            Case("above 4K short side", regular.copy(width = 3840, height = 2162)),
            Case("unknown depth", regular.copy(bitDepth = ColorDepth.UNKNOWN)),
            Case("unknown chroma", regular.copy(chroma = ChromaFormat.UNKNOWN)),
            Case("unknown width", regular.copy(width = 0)),
            Case("unknown height", regular.copy(height = 0)),
            Case("missing MIME", regular.copy(mime = ""))
        )
        cases.forEach { case ->
            val plan = Compatibility.plan(case.source, case.decoded, case.capabilities)
            assertEquals(case.name, case.direct, plan is SourcePlan.Direct)
            if (plan is SourcePlan.Proxy) assertTrue(case.name, plan.reason.isNotBlank())
        }
    }

    @Test fun proxyTargetsStayEvenBoundedAndDoNotOverflow() {
        val sources = listOf(
            regular.copy(width = 7680, height = 4320),
            regular.copy(width = 4320, height = 7680),
            regular.copy(width = Int.MAX_VALUE, height = Int.MAX_VALUE),
            regular.copy(width = Int.MAX_VALUE, height = 1),
            regular.copy(width = 1, height = Int.MAX_VALUE),
            regular.copy(width = 641, height = 481),
            regular.copy(width = 1, height = 1),
            regular.copy(width = -1, height = 0)
        )
        sources.forEach { source ->
            val target = (Compatibility.plan(source, false, device) as SourcePlan.Proxy).target
            assertTrue(target.width >= 2 && target.height >= 2)
            assertEquals(0, target.width % 2)
            assertEquals(0, target.height % 2)
            assertTrue(max(target.width, target.height) <= 1920)
            assertTrue(min(target.width, target.height) <= 1080)
            assertEquals(6_000_000, target.bitrate)
            assertEquals(30, target.fps)
        }
        assertEquals(ProxySpec(1920, 1080, 6_000_000, 30),
            (Compatibility.plan(sources[0], false, device) as SourcePlan.Proxy).target)
        assertEquals(ProxySpec(1080, 1920, 6_000_000, 30),
            (Compatibility.plan(sources[1], false, device) as SourcePlan.Proxy).target)
        assertEquals(ProxySpec(640, 480, 6_000_000, 30),
            (Compatibility.plan(sources[5], false, device) as SourcePlan.Proxy).target)
    }

    @Test fun requestingProxyDoesNotClaimTranscodingIsPossible() {
        val plan = Compatibility.plan(regular, decoded = false, device = device)
        assertTrue(plan is SourcePlan.Proxy)
        assertTrue((plan as SourcePlan.Proxy).reason.contains("decodificador"))
    }

    @Test fun rotatedProxyMatchesDisplayOrientationWithoutChangingEncodedDimensions() {
        val source = regular.copy(width = 1080, height = 1920, rotationDegrees = 90)
        val target = (Compatibility.plan(source, decoded = false, device = device) as SourcePlan.Proxy).target
        assertEquals(ProxySpec(1920, 1080, 6_000_000, 30), target)
        assertEquals(1080, source.width)
        assertEquals(1920, source.height)
        assertEquals(90, source.rotationDegrees)
        listOf(270, -90, 450).forEach { rotation ->
            assertEquals(target, (Compatibility.plan(source.copy(rotationDegrees = rotation), false, device) as SourcePlan.Proxy).target)
        }
    }
}
