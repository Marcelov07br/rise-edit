package br.com.riseedit.core

import br.com.riseedit.core.color.ColorGrade
import br.com.riseedit.core.color.LogProfile
import br.com.riseedit.core.editing.AttributeCopy
import br.com.riseedit.core.editing.AttributeGroup
import br.com.riseedit.core.editing.ClipAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttributeCopyTest {
    private val curves =
        ColorCurves(master = listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0.6f), CurvePoint(1f, 1f)))
    private val src = Clip(uri = "a", name = "a", sourceDurationMs = 1000).copy(
        adjustments = ColorAdjust(
            exposure = 1f,
            curves = curves,
            hsl = HslBands().with(2, 1, 0.5f)
        ),
        grade = ColorGrade(
            log = LogProfile.SLOG3,
            lutUri = "file:///l.cube",
            lutName = "l",
            lutBeforeAdjust = true
        ),
        volume = 0.5f
    )
    private val dst = Clip(uri = "b", name = "b", sourceDurationMs = 1000).copy(
        adjustments = ColorAdjust(contrast = 0.3f, wheels = ColorWheels(gain = Wheel(luma = 0.2f))),
        fadeMs = 300
    )

    @Test fun availableListsOnlyNonDefault() {
        val av = AttributeCopy.available(ClipAttributes.of(src))
        assertEquals(
            listOf(
                AttributeGroup.COLOR_BASIC,
                AttributeGroup.COLOR_CURVES,
                AttributeGroup.COLOR_HSL,
                AttributeGroup.LOG_PROFILE,
                AttributeGroup.LUT,
                AttributeGroup.GRADE_ORDER,
                AttributeGroup.VOLUME
            ),
            av
        )
    }

    @Test fun checkingDependentChecksDependency() {
        val shown = AttributeCopy.available(ClipAttributes.of(src))
        val s = AttributeCopy.toggle(emptySet(), AttributeGroup.GRADE_ORDER, true, shown)
        assertEquals(setOf(AttributeGroup.GRADE_ORDER, AttributeGroup.LUT), s)
    }

    @Test fun uncheckingDependencyDropsDependent() {
        val s = AttributeCopy.toggle(
            setOf(AttributeGroup.LUT, AttributeGroup.GRADE_ORDER),
            AttributeGroup.LUT,
            false,
            emptyList()
        )
        assertTrue(s.isEmpty())
        assertEquals(
            setOf(AttributeGroup.COLOR_HSL),
            AttributeCopy.close(setOf(AttributeGroup.COLOR_HSL, AttributeGroup.GRADE_ORDER))
        )
    }

    @Test fun pasteOnlySelectedGroupsKeepsTheRest() {
        val clip = ClipAttributes.of(
            src,
            setOf(AttributeGroup.COLOR_CURVES, AttributeGroup.COLOR_BASIC, AttributeGroup.VOLUME)
        )
        val out = clip.applyTo(dst, setOf(AttributeGroup.COLOR_CURVES, AttributeGroup.VOLUME))
        assertEquals(curves, out.adjustments.curves)
        assertEquals(0.3f, out.adjustments.contrast) // basic unchecked on paste
        assertEquals(0.2f, out.adjustments.wheels.gain.luma) // not in clipboard
        assertEquals(0.5f, out.volume)
        assertEquals(300L, out.fadeMs)
        assertEquals(LogProfile.NONE, out.grade.log)
    }

    @Test fun clipboardHidesGroupsNotCopied() {
        val clip = ClipAttributes.of(
            src,
            setOf(AttributeGroup.COLOR_HSL, AttributeGroup.GRADE_ORDER)
        )
        // GRADE_ORDER dropped: LUT missing.
        assertEquals(listOf(AttributeGroup.COLOR_HSL), clip.available)
        assertTrue(clip.resources().isEmpty())
        assertEquals(listOf("file:///l.cube"), ClipAttributes.of(src).resources())
    }

    @Test fun basicPasteKeepsTargetAdvancedColor() {
        val out = ClipAttributes.of(src, setOf(AttributeGroup.COLOR_BASIC)).applyTo(dst)
        assertEquals(1f, out.adjustments.exposure)
        assertEquals(0f, out.adjustments.contrast)
        assertEquals(0.2f, out.adjustments.wheels.gain.luma)
        assertFalse(out.adjustments.curves != ColorCurves())
    }
}
