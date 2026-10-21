package br.com.riseedit.core

import br.com.riseedit.core.color.ColorGrade
import br.com.riseedit.core.color.CubeLut
import br.com.riseedit.core.color.LogProfile
import br.com.riseedit.core.color.Meters
import br.com.riseedit.core.color.ToneCurve
import br.com.riseedit.core.editing.ClipAttributes
import br.com.riseedit.core.editing.JLCut
import br.com.riseedit.core.editing.Markers
import br.com.riseedit.core.editing.RippleDelete
import br.com.riseedit.core.editing.SeparateAudio
import br.com.riseedit.core.motion.ByteSource
import br.com.riseedit.core.motion.TelemetryKind
import br.com.riseedit.core.motion.TelemetryProbe
import org.junit.Assert
import org.junit.Assert.assertNull
import org.junit.Test

private fun assertEquals(e: Long, a: Long) = Assert.assertEquals(e, a)
private fun assertEquals(e: Long, a: Long?) = Assert.assertEquals(e, a)
private fun assertEquals(e: Double, a: Double, d: Double) = Assert.assertEquals(e, a, d)
private fun assertEquals(e: Float, a: Float, d: Float = 0f) = Assert.assertEquals(e, a, d)
private fun assertEquals(e: Any?, a: Any?) = Assert.assertEquals(e, a)
private fun assertTrue(c: Boolean, m: String = "") = Assert.assertTrue(m, c)

class P3Test {
    private fun clip(id: String, ms: Long = 10_000) =
        Clip(id = id, uri = "u$id", name = id, sourceDurationMs = ms)

    @Test
    fun rippleDeleteShiftsLaterLayersAndDropsLinkedAudio() {
        var p = Project(clips = listOf(clip("a"), clip("b"), clip("c")))
        p = SeparateAudio.apply(p, 1)
        p = p.copy(
            texts = listOf(TextLayer(text = "x", startMs = 21_000, endMs = 22_000)),
            audio =
            p.audio +
                AudioClip(uri = "m", name = "m", sourceDurationMs = 5000, offsetMs = 25_000),
            markers = listOf(Marker(timeMs = 15_000), Marker(timeMs = 28_000))
        )
        val r = RippleDelete.clip(p, 1)
        assertEquals(listOf("a", "c"), r.clips.map { it.id })
        assertEquals(11_000, r.texts.single().startMs)
        assertEquals(listOf(15_000L), r.audio.map { it.offsetMs })
        assertEquals(listOf(18_000L), r.markers.map { it.timeMs })
    }

    @Test
    fun markersToggleAndNavigate() {
        var p = Project(clips = listOf(clip("a")))
        p = Markers.toggle(p, 2000)
        p = Markers.toggle(p, 5000)
        assertEquals(5000, Markers.next(p, 2000))
        assertEquals(2000, Markers.previous(p, 5000))
        p = Markers.toggle(p, 2050)
        assertEquals(listOf(5000L), p.markers.map { it.timeMs })
    }

    @Test
    fun jAndLCutsExtendLinkedAudioAndSurviveTrim() {
        var p = Project(clips = listOf(clip("a"), clip("b").trim(2000, 8000)))
        p = SeparateAudio.apply(p, 1)
        val id = p.audio.single().id
        p = JLCut.set(p, id, leadMs = 1000, tailMs = 1500)
        val a = p.audio.single()
        assertEquals(1000, a.startMs)
        assertEquals(9000, a.offsetMs)
        assertEquals(9500, a.endMs)
        p = SeparateAudio.sync(p.replace(1, p.clips[1].trim(3000, 8000)), p.clips[1].id)
        assertEquals(2000, p.audio.single().startMs)
    }

    @Test
    fun pasteAttributesKeepsTiming() {
        val src = clip(
            "a"
        ).copy(
            adjustments = ColorAdjust(brightness = 0.2f),
            grade = ColorGrade(curve = ToneCurve.S_SOFT)
        )
        val dst = clip("b").trim(1000, 4000)
        val out = ClipAttributes.of(src).applyTo(dst)
        assertEquals(0.2f, out.adjustments.brightness)
        assertEquals(ToneCurve.S_SOFT, out.grade.curve)
        assertEquals(1000, out.startMs)
    }

    @Test
    fun exportNeverOffersFakeSixty() {
        val slow =
            Project(
                clips = listOf(
                    clip(
                        "a"
                    ).copy(sourceInfo = SourceInfo("video/avc", 1920, 1080, frameRate = 29.97f))
                )
            )
        assertEquals(listOf(24, 30), ExportPlan.allowedFps(slow))
        assertEquals(30, ExportPlan.effectiveFps(slow.copy(export = ExportSettings(fps = 60))))
        val fast = slow.copy(
            clips = listOf(
                clip("a").copy(sourceInfo = SourceInfo("video/avc", 3840, 2160, frameRate = 59.94f))
            )
        )
        assertEquals(listOf(24, 30, 60), ExportPlan.allowedFps(fast))
        ExportSettings(2160, 60, 150_000_000, VideoCodec.HEVC)
        assertTrue(ExportPlan.upscales(slow.copy(export = ExportSettings(height = 2160))))
    }

    @Test
    fun logDecodesMiddleGrey() {
        assertEquals(0.18, LogProfile.SLOG3.toLinear(420.0 / 1023), 1e-6)
        assertEquals(0.18, LogProfile.VLOG.toLinear(0.423), 0.01)
        assertEquals(0.18, LogProfile.APPLE_LOG.toLinear(0.4882), 0.01)
        val grey = ColorGrade(log = LogProfile.SLOG3).apply(DoubleArray(3) { 420.0 / 1023 }, null)
        assertTrue(grey[0] in 0.35..0.5, "grey ${grey[0]}")
    }

    @Test
    fun identityCubeAndCurves() {
        val cube = ColorGrade().cube(5, null)
        assertEquals(0.25f, cube[3], 1e-6f)
        assertEquals(0.5, ToneCurve.S_STRONG.apply(0.5), 1e-9)
        assertTrue(ToneCurve.S_STRONG.apply(0.25) < 0.25)
        val text = "TITLE \"id\"\nLUT_3D_SIZE 2\n" +
            (0 until 8).joinToString("\n") { i -> "${i and 1} ${i shr 1 and 1} ${i shr 2 and 1}" }
        val lut = CubeLut.parse(text)
        assertEquals(0.3, lut.sample(0.3, 0.6, 0.9)[0], 1e-6)
        assertEquals(0.9, lut.sample(0.3, 0.6, 0.9)[2], 1e-6)
    }

    @Test
    fun metersCountLuma() {
        val px = IntArray(100) { if (it < 10) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        assertEquals(10, Meters.histogram(px)[255])
        assertEquals(10, Meters.zebra(px).count { it })
        assertEquals(0.1, Meters.clipped(px), 1e-9)
        assertEquals(100, Meters.waveform(px, 10, 5).sumOf { it.sum() })
    }

    @Test
    fun subtitlesMapThroughTrimAndSpeed() {
        val c = clip("a").trim(1000, 9000).copy(speedPoints = listOf(SpeedPoint(1000, 2.0)))
        val segs = Subtitles.parse("0\t2000\t Olá\n2000\t4000\t[Música]\n")
        val mapped = Subtitles.mapClip(segs, c, 5000)
        assertEquals(1, mapped.size)
        assertEquals(5000, mapped[0].startMs)
        assertEquals(6000, mapped[0].endMs)
        val layers = Subtitles.layers(mapped, 10_000)
        assertEquals("Olá", layers.single().text)
        assertNull(c.outputAt(500))
    }

    @Test
    fun telemetryFindsCammTrack() {
        fun box(type: String, body: ByteArray): ByteArray {
            val size = 8 + body.size
            return byteArrayOf(
                (size ushr 24).toByte(),
                (size ushr 16).toByte(),
                (size ushr 8).toByte(),
                size.toByte()
            ) +
                type.toByteArray() + body
        }
        val stsd = box("stsd", ByteArray(8) + box("camm", ByteArray(8)))
        val file =
            box("ftyp", ByteArray(8)) +
                box("moov", box("trak", box("mdia", box("minf", box("stbl", stsd)))))
        val src = ByteSource { pos, len ->
            file.copyOfRange(
                pos.toInt(),
                minOf(
                    file.size,
                    pos.toInt() + len
                )
            )
        }
        val report = TelemetryProbe.scan(src, file.size.toLong())
        assertTrue(TelemetryKind.CAMM in report.kinds)
        assertTrue(report.hasGyro)
    }
}
