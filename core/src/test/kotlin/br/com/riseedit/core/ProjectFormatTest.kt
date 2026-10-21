package br.com.riseedit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFormatTest {
    @Test
    fun ntscRatesAreFractionalAndPalIntegers() {
        val ntsc = ProjectFormat.rates(TvStandard.NTSC)
        assertEquals(
            listOf("23,976 fps", "24 fps", "29,97 fps", "30 fps", "59,94 fps", "60 fps"),
            ntsc.map {
                it.label
            }
        )
        assertEquals(29.97, FrameRateChoice(30, true).rate, 0.001)
        assertEquals(listOf(25, 50, 24), ProjectFormat.rates(TvStandard.PAL).map { it.fps })
        assertTrue(ProjectFormat.rates(TvStandard.PAL).none { it.ntsc })
    }

    @Test
    fun switchingStandardCoercesTheRate() {
        assertEquals(
            FrameRateChoice(25, false),
            ProjectFormat.coerce(FrameRateChoice(30, true), TvStandard.PAL)
        )
        assertEquals(
            FrameRateChoice(24, false),
            ProjectFormat.coerce(FrameRateChoice(24, false), TvStandard.PAL)
        )
        assertEquals(
            FrameRateChoice(30, true),
            ProjectFormat.coerce(FrameRateChoice(50, false), TvStandard.NTSC)
        )
    }

    @Test
    fun newProjectCarriesFormatIntoExport() {
        val p = ProjectFormat.newProject(
            "X",
            Aspect.VERTICAL,
            Resolution.UHD,
            FrameRateChoice(60, true)
        )
        assertEquals(9f / 16f, p.aspectRatio, 1e-6f)
        assertEquals(2160, p.export.height)
        assertEquals(59.94, p.export.frameRate, 0.001)
        val pal = ProjectFormat.newProject(
            "Y",
            Aspect.WIDE,
            Resolution.FHD,
            FrameRateChoice(25, false)
        )
        assertEquals(25.0, pal.export.frameRate, 0.0)
        assertEquals(TvStandard.PAL, ProjectFormat.standardOf(25, false))
    }

    @Test
    fun exportPlanKeepsNtscFraction() {
        val clip = Clip(uri = "u", name = "a", sourceDurationMs = 1000, sourceInfo = null)
        val p = Project(clips = listOf(clip), export = ExportSettings(fps = 30, ntsc = true))
        assertEquals(29.97, ExportPlan.effectiveFrameRate(p), 0.001)
        assertFalse(runCatching { ExportSettings(fps = 25, ntsc = true) }.isSuccess)
    }
}
