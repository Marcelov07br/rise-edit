package br.com.riseedit.core.mask

import br.com.riseedit.core.AdjustmentLayer
import br.com.riseedit.core.AdjustmentStack
import br.com.riseedit.core.Clip
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.editing.AttributeCopy
import br.com.riseedit.core.editing.AttributeGroup
import br.com.riseedit.core.editing.ClipAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskTest {
    private val eps = 1e-3f
    private fun cov(m: Mask, u: Float, v: Float, aspect: Float = 1f) =
        MaskMath.single(m, u, v, aspect)

    @Test fun rectangleInsideOutsideAndRoundedCorner() {
        val r =
            Mask(
                type = MaskType.RECT,
                geom = MaskGeom(0.5f, 0.5f, 0.4f, 0.4f, 0f, 0f),
                roundness = 0f
            )
        assertEquals(1f, cov(r, 0.5f, 0.5f), eps)
        assertEquals(1f, cov(r, 0.68f, 0.68f), eps) // inside a sharp corner
        assertEquals(0f, cov(r, 0.8f, 0.5f), eps)
        val round = r.copy(roundness = 1f)
        assertEquals(0f, cov(round, 0.69f, 0.69f), eps) // cut by the radius
    }

    @Test fun ellipseRespectsAspectAndRotation() {
        // Circle on a 16:9 frame: w is a fraction of width, so w = h * 9/16 is round.
        val a = 16f / 9f
        val c = Mask(type = MaskType.ELLIPSE, geom = MaskGeom(0.5f, 0.5f, 0.5f / a, 0.5f, 0f, 0f))
        assertEquals(1f, cov(c, 0.5f + 0.2f / a, 0.5f, a), eps)
        assertEquals(1f, cov(c, 0.5f, 0.7f, a), eps)
        assertEquals(0f, cov(c, 0.5f, 0.8f, a), eps)
        val wide = Mask(type = MaskType.ELLIPSE, geom = MaskGeom(0.5f, 0.5f, 0.8f, 0.1f, 0f, 0f))
        assertEquals(1f, cov(wide, 0.8f, 0.5f), eps)
        val turned = wide.copy(geom = wide.geom.copy(rotation = 90f))
        assertEquals(0f, cov(turned, 0.8f, 0.5f), eps)
        assertEquals(1f, cov(turned, 0.5f, 0.8f), eps)
    }

    @Test fun featherIsMonotoneAndHalfAtEdge() {
        val m = Mask(type = MaskType.ELLIPSE, geom = MaskGeom(0.5f, 0.5f, 0.4f, 0.4f, 0f, 0.2f))
        assertEquals(0.5f, cov(m, 0.7f, 0.5f), 0.01f) // edge (radius 0.2) = middle of the ramp
        var last = 2f
        for (i in 0..20) {
            val v = cov(m, 0.5f + i * 0.02f, 0.5f)
            assertTrue(v <= last + 1e-6f)
            last = v
        }
        val hard = m.copy(geom = m.geom.copy(feather = 0f))
        assertEquals(1f, cov(hard, 0.69f, 0.5f), eps)
        assertTrue(cov(m, 0.69f, 0.5f) < 0.9f)
    }

    @Test fun gradientsLinearAndRadial() {
        val lin = Mask.new(MaskType.LINEAR).copy(geom = MaskGeom(0.5f, 0.5f, 1f, 0.4f, 0f, 0f))
        assertEquals(1f, cov(lin, 0.5f, 0.2f), eps)
        assertEquals(0.5f, cov(lin, 0.5f, 0.5f), eps)
        assertEquals(0f, cov(lin, 0.5f, 0.8f), eps)
        val rad = Mask(type = MaskType.RADIAL, geom = MaskGeom(0.5f, 0.5f, 0.6f, 0.6f, 0f, 1f))
        assertEquals(1f, cov(rad, 0.5f, 0.5f), eps)
        assertTrue(cov(rad, 0.65f, 0.5f) in 0.2f..0.8f)
        assertEquals(0f, cov(rad, 0.85f, 0.5f), eps)
    }

    @Test fun invertAndOpacity() {
        val m =
            Mask(type = MaskType.RECT, geom = MaskGeom(feather = 0f), invert = true, opacity = 0.5f)
        assertEquals(0f, cov(m, 0.5f, 0.5f), eps)
        assertEquals(0.5f, cov(m, 0.05f, 0.05f), eps)
    }

    @Test fun colorMaskUsesQualifierKey() {
        val m = Mask.new(
            MaskType.COLOR
        ).copy(
            qualifier = br.com.riseedit.core.color.Qualifier(active = true, hue = 0f, satLow = 0.3f)
        )
        assertTrue(MaskMath.single(m, 0.5f, 0.5f, 1f, floatArrayOf(0.9f, 0.1f, 0.1f)) > 0.9f)
        assertEquals(0f, MaskMath.single(m, 0.5f, 0.5f, 1f, floatArrayOf(0.1f, 0.1f, 0.9f)), eps)
    }

    @Test fun combinationAddSubtractIntersect() {
        assertEquals(1f, MaskMath.combine(emptyList()), eps)
        assertEquals(0.75f, MaskMath.combine(listOf(MaskOp.ADD to 0.5f, MaskOp.ADD to 0.5f)), eps)
        assertEquals(0.5f, MaskMath.combine(listOf(MaskOp.ADD to 1f, MaskOp.SUBTRACT to 0.5f)), eps)
        assertEquals(
            0.25f,
            MaskMath.combine(listOf(MaskOp.ADD to 0.5f, MaskOp.INTERSECT to 0.5f)),
            eps
        )
        // First mask always starts the union, whatever its op.
        assertEquals(0.6f, MaskMath.combine(listOf(MaskOp.SUBTRACT to 0.6f)), eps)
        val big =
            Mask(
                type = MaskType.RECT,
                geom = MaskGeom(0.5f, 0.5f, 0.8f, 0.8f, 0f, 0f),
                roundness = 0f
            )
        val hole =
            Mask(
                type = MaskType.ELLIPSE,
                geom = MaskGeom(0.5f, 0.5f, 0.2f, 0.2f, 0f, 0f),
                op = MaskOp.SUBTRACT
            )
        assertEquals(0f, MaskMath.coverage(listOf(big, hole), 0.5f, 0.5f, 1f), eps)
        assertEquals(1f, MaskMath.coverage(listOf(big, hole), 0.3f, 0.5f, 1f), eps)
    }

    @Test fun setIsCappedAtFour() {
        var s = MaskSet()
        repeat(6) { s = s.add(Mask.new(MaskType.RECT)) }
        assertEquals(MaskSet.MAX, s.masks.size)
    }

    @Test fun keyframesInterpolateSmoothlyAndShortestRotation() {
        val m = Mask.new(MaskType.ELLIPSE)
            .toggleKey(0)
            .let { it.withGeomAt(1000, it.geom.copy(cx = 0.9f, rotation = -170f)) }
            .let {
                it.copy(
                    keys = listOf(
                        it.keys[0].copy(geom = it.keys[0].geom.copy(cx = 0.1f, rotation = 170f)),
                        it.keys[1]
                    )
                )
            }
        assertEquals(2, m.keys.size)
        assertEquals(0.1f, m.geomAt(-5).cx, eps)
        assertEquals(0.5f, m.geomAt(500).cx, eps)
        assertTrue(m.geomAt(100).cx < 0.1f + 0.08f * 0.5f) // ease-in (slower than linear)
        assertEquals(180f, kotlin.math.abs(m.geomAt(500).rotation), 0.5f) // via ±180, not via 0
        assertEquals(0.9f, m.geomAt(2000).cx, eps)
        // Removing the last key bakes its value into the static geometry.
        val one = m.toggleKey(1000)
        val none = one.toggleKey(0)
        assertTrue(none.keys.isEmpty())
        assertEquals(0.1f, none.geom.cx, eps)
    }

    @Test fun clipColorAtCarriesEvaluatedMasks() {
        val m = Mask.new(MaskType.RECT).toggleKey(0).let {
            it.withGeomAt(2000, it.geom.copy(cx = 1f))
        }
        val clip = Clip(uri = "x", name = "x", sourceDurationMs = 4000, masks = MaskSet(listOf(m)))
        val (a, _) = clip.colorAt(1000)
        assertEquals(1, a.masks.size)
        assertTrue(a.masks[0].keys.isEmpty())
        assertEquals(0.75f, a.masks[0].geom.cx, eps)
        assertTrue(a.isNeutral) // masks alone don't make a look
        assertTrue(
            Clip(uri = "x", name = "x", sourceDurationMs = 10).colorAt(0).first.masks.isEmpty()
        )
    }

    @Test fun adjustmentLayerMixesOnlyInsideMask() {
        val inside =
            Mask(
                type = MaskType.RECT,
                geom = MaskGeom(0.25f, 0.5f, 0.5f, 1f, 0f, 0f),
                roundness = 0f
            )
        val layer =
            AdjustmentLayer(
                startMs = 0,
                durationMs = 1000,
                adjust = ColorAdjust(exposure = 1f),
                masks = MaskSet(listOf(inside))
            )
        val passes = AdjustmentStack.at(listOf(layer), 500)
        val rgb = floatArrayOf(0.3f, 0.3f, 0.3f)
        val inPx = AdjustmentStack.applyCpu(passes, rgb, 0.2f, 0.5f, 1f)
        val outPx = AdjustmentStack.applyCpu(passes, rgb, 0.8f, 0.5f, 1f)
        assertNotEquals(0.3f, inPx[0], eps)
        assertEquals(0.3f, outPx[0], eps)
    }

    @Test fun jsonRoundTripAndMigration() {
        val s = MaskSet(
            listOf(
                Mask.new(
                    MaskType.RECT
                ).copy(invert = true, opacity = 0.7f, roundness = 0.5f).toggleKey(100),
                Mask.new(MaskType.COLOR).copy(op = MaskOp.INTERSECT),
                Mask.new(MaskType.LINEAR).copy(op = MaskOp.SUBTRACT, enabled = false)
            )
        )
        assertEquals(s, MaskSet.fromMap(MaskSet.toMap(s)))
        assertEquals(MaskSet(), MaskSet.fromMap(null)) // projects ≤ 0.16
        val unknown = MaskSet.fromMap(
            mapOf(
                "v" to 1,
                "masks" to listOf(mapOf("type" to "star"), mapOf("type" to "ellipse"))
            )
        )
        assertEquals(1, unknown.masks.size)
        val layer = AdjustmentLayer(masks = s)
        assertEquals(s, AdjustmentLayer.fromMap(AdjustmentLayer.toMap(layer))!!.masks)
        assertEquals(
            MaskSet(),
            AdjustmentLayer.fromMap(AdjustmentLayer.toMap(AdjustmentLayer()))!!.masks
        )
    }

    @Test fun copyPasteAsGroupWithFreshIds() {
        val src =
            Clip(
                uri = "a",
                name = "a",
                sourceDurationMs = 1000,
                masks = MaskSet(listOf(Mask.new(MaskType.ELLIPSE)))
            )
        val dst = Clip(uri = "b", name = "b", sourceDurationMs = 1000)
        val attrs = ClipAttributes.of(src)
        assertTrue(AttributeGroup.MASKS in AttributeCopy.available(attrs))
        val out = attrs.applyTo(dst, setOf(AttributeGroup.MASKS))
        assertEquals(1, out.masks.masks.size)
        assertNotEquals(src.masks.masks[0].id, out.masks.masks[0].id)
        assertEquals(src.masks.masks[0].geom, out.masks.masks[0].geom)
        assertTrue(attrs.applyTo(dst, setOf(AttributeGroup.COLOR_BASIC)).masks.isEmpty)
        // Layer ↔ clip.
        val layer = AdjustmentLayer().withColorOf(out)
        assertEquals(1, layer.masks.masks.size)
    }

    @Test fun glslDeclaresEveryUniform() {
        MaskMath.uniforms(listOf(Mask())).keys.forEach { assertTrue(it, it in MaskMath.GLSL) }
    }
}
