package br.com.riseedit.settings

import br.com.riseedit.Accent
import br.com.riseedit.ThemeMode
import br.com.riseedit.core.Aspect
import br.com.riseedit.core.FrameRateChoice
import br.com.riseedit.core.Resolution
import br.com.riseedit.core.TvStandard
import br.com.riseedit.media.StillClip
import br.com.riseedit.picker.PickerSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsLogicTest {
    @Test fun roundTrip() {
        val s = AppSettings(
            theme = ThemeMode.LIGHT, accent = Accent.MINT, haptics = false, reduceMotion = true,
            aspect = Aspect.PORTRAIT, resolution = Resolution.UHD, standard = TvStandard.PAL,
            rate = FrameRateChoice(
                50,
                false
            ),
            exportFolder = "content://t", snap = false, ripple = true,
            thumbs = ThumbDensity.HIGH, imageDurationMs = 5000
        )
        assertEquals(s, AppSettings.fromMap(s.toMap()))
    }

    @Test fun garbageFallsBack() {
        assertEquals(
            AppSettings(),
            AppSettings.fromMap(
                mapOf(
                    "theme" to "x",
                    "rate" to "999",
                    "imageDurationMs" to "-3"
                )
            )
        )
    }

    @Test fun exportDefaultsCarryNtsc() {
        val e = AppSettings(
            rate = FrameRateChoice(30, true),
            resolution = Resolution.HD
        ).exportDefaults()
        assertEquals(720, e.height)
        assertEquals(29.97, e.frameRate, 0.001)
    }

    @Test fun stillSize() {
        assertEquals(1920 to 1080, StillClip.size(4000, 2250))
        assertEquals(1080 to 1920, StillClip.size(3024, 5376))
        assertEquals(800 to 600, StillClip.size(801, 601))
    }

    @Test fun pickerDuration() {
        assertEquals("0:03", PickerSelection.duration(2_600))
        assertEquals("1:02:05", PickerSelection.duration(3_725_000))
    }

    @Test fun folder() {
        assertEquals(
            "Movies/Rise",
            folderLabel(
                "content://com.android.externalstorage.documents/tree/primary%3AMovies%2FRise"
            )
        )
    }

    @Test fun pickerOrder() {
        var s = PickerSelection.toggle(emptyList(), "b")
        s = PickerSelection.toggle(s, "a")
        assertEquals(2, PickerSelection.order(s, "a"))
        s = PickerSelection.toggle(s, "b")
        assertEquals(1, PickerSelection.order(s, "a"))
        assertNull(PickerSelection.order(s, "b"))
    }
}
