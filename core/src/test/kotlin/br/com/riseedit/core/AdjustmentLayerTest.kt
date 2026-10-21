package br.com.riseedit.core

import br.com.riseedit.core.color.ColorGrade
import br.com.riseedit.core.color.ColorKeyEdits
import br.com.riseedit.core.color.ColorKeyGroup
import br.com.riseedit.core.color.Vignette
import br.com.riseedit.core.editing.AttributeGroup
import br.com.riseedit.core.editing.ClipAttributes
import br.com.riseedit.core.editing.TrimEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdjustmentLayerTest {
    private val warm = ColorAdjust(warmth = 0.5f)
    private val dark = ColorAdjust(exposure = -1f)

    @Test fun timeWindowIsEndExclusive() {
        val l = AdjustmentLayer(startMs = 1000, durationMs = 500, adjust = warm)
        assertTrue(!l.covers(999) && l.covers(1000) && l.covers(1499) && !l.covers(1500))
        assertEquals(0, AdjustmentStack.at(listOf(l), 1500).size)
        assertEquals(1, AdjustmentStack.at(listOf(l), 1200).size)
    }

    @Test fun orderBottomToTop() {
        val a = AdjustmentLayer(id = "a", startMs = 0, durationMs = 4000, track = 1, adjust = warm)
        val b =
            AdjustmentLayer(id = "b", startMs = 500, durationMs = 4000, track = 0, adjust = dark)
        val c =
            AdjustmentLayer(id = "c", startMs = 100, durationMs = 4000, track = 0, adjust = dark)
        assertEquals(
            listOf("c", "b", "a"),
            AdjustmentStack.at(listOf(a, b, c), 1000).map {
                it.layerId
            }
        )
    }

    @Test fun neutralAndTransparentLayersAreSkipped() {
        val n = AdjustmentLayer(startMs = 0, durationMs = 1000)
        val z = AdjustmentLayer(startMs = 0, durationMs = 1000, adjust = warm, opacity = 0f)
        assertTrue(AdjustmentStack.at(listOf(n, z), 10).isEmpty())
    }

    @Test fun opacityMixesWithBelowAndOrderMatters() {
        val gray = floatArrayOf(0.5f, 0.5f, 0.5f)
        val full = AdjustmentPass("x", dark, ColorGrade(), 1f)
        val half = full.copy(opacity = 0.5f)
        val f = AdjustmentStack.applyCpu(listOf(full), gray)[0]
        val h = AdjustmentStack.applyCpu(listOf(half), gray)[0]
        assertEquals((0.5f + f) / 2f, h, 1e-4f)
        // contrast then exposure != exposure then contrast
        val con = AdjustmentPass("c", ColorAdjust(contrast = 0.5f), ColorGrade(), 1f)
        val px = floatArrayOf(0.7f, 0.6f, 0.3f)
        val ab = AdjustmentStack.applyCpu(listOf(con, full), px)
        val ba = AdjustmentStack.applyCpu(listOf(full, con), px)
        assertTrue(kotlin.math.abs(ab[0] - ba[0]) > 1e-3f)
    }

    @Test fun keysAreLayerLocalAndTrimKeepsProjectTime() {
        var l = AdjustmentLayer(startMs = 2000, durationMs = 2000)
        val clip = ColorKeyEdits.toggle(
            l.asClip().copy(adjustments = warm),
            ColorKeyGroup.BASIC,
            500
        )
        l = l.withColorOf(clip)
        assertEquals(listOf(500L), l.colorKeys.times())
        val t = l.trimmed(TrimEdge.START, 300)
        assertEquals(2300, t.startMs)
        assertEquals(1700, t.durationMs)
        assertEquals(listOf(200L), t.colorKeys.times()) // still at project 2500
        assertEquals(AdjustmentLayer.MIN_MS, l.trimmed(TrimEdge.END, -10_000).durationMs)
        assertEquals(0, l.trimmed(TrimEdge.START, -10_000).startMs)
    }

    @Test fun jsonRoundTripAndAbsentMeansNone() {
        val l =
            AdjustmentLayer(
                startMs = 100,
                durationMs = 900,
                track = 1,
                name = "Look",
                opacity = 0.6f,
                adjust = ColorAdjust(vignette = Vignette(-0.5f))
            )
        val m = AdjustmentLayer.toMap(l)
        assertEquals(l, AdjustmentLayer.fromMap(m))
        assertNull(AdjustmentLayer.fromMap(mapOf("start" to 1)))
        assertTrue(Project().adjustmentLayers.isEmpty())
    }

    @Test fun copyPasteBetweenLayerAndClip() {
        val layer =
            AdjustmentLayer(adjust = ColorAdjust(exposure = 0.4f, vignette = Vignette(-0.3f)))
        val clip = Clip(uri = "a", name = "a", sourceDurationMs = 1000, volume = 0.5f)
        val pasted = ClipAttributes.of(
            layer.asClip()
        ).applyTo(clip, setOf(AttributeGroup.COLOR_VIGNETTE, AttributeGroup.COLOR_BASIC))
        assertEquals(0.4f, pasted.adjustments.exposure)
        assertEquals(0.5f, pasted.volume)
        val back = layer.withColorOf(
            ClipAttributes.of(pasted).applyTo(layer.asClip(), setOf(AttributeGroup.COLOR_BASIC))
        )
        assertEquals(-0.3f, back.adjust.vignette.amount)
        assertEquals(
            Selection.Adjustment(layer.id),
            Selection.of(Project(adjustmentLayers = listOf(layer)), layer.id)
        )
    }

    @Test fun newLayerFitsProject() {
        val p = Project(clips = listOf(Clip(uri = "a", name = "a", sourceDurationMs = 2000)))
        val l = AdjustmentLayer.at(p, 1500)
        assertEquals(1500, l.startMs)
        assertEquals(500, l.durationMs)
        assertEquals(1, AdjustmentStack.clamp(listOf(l.copy(durationMs = 5000)), 2000).size)
        assertEquals(
            500,
            AdjustmentStack.clamp(listOf(l.copy(durationMs = 5000)), 2000)[0].durationMs
        )
    }
}
