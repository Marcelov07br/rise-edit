package br.com.riseedit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordMotionTest {
    @Test fun springIsLowBounceAndContinuous() {
        val v = (0..200).map { Easing.spring(it / 200f) }
        assertEquals(0f, v.first(), 0f)
        assertEquals(1f, v.last(), 0f)
        assertTrue("overshoot ≤ 6 %", v.max() <= 1.06f)
        // No jumps: consecutive samples never differ by more than 6 % of the range.
        assertTrue(v.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) < 0.06f })
    }

    @Test fun inOutUseEasedCurvesNotLinear() {
        val style = TextStyle(animationIn = TextAnimation.FADE, animationOut = TextAnimation.NONE, animationMs = 400)
        val quarter = TextMotion.at(style, 100, 3000).alpha
        assertTrue("ease-out: ahead of linear at 25 %", quarter > 0.4f)
    }

    @Test fun popHidesUntilSpokenThenSettles() {
        val style = TextStyle(wordAnimation = WordAnimation.POP)
        assertEquals(0f, WordMotion.at(style, 500L until 900L, 400).alpha, 0f)
        val settled = WordMotion.at(style, 500L until 900L, 500 + style.wordMs)
        assertEquals(1f, settled.scale, 1e-4f)
        assertEquals(1f, settled.alpha, 0f)
    }

    @Test fun karaokeHighlightsSpokenWords() {
        val style = TextStyle(wordAnimation = WordAnimation.KARAOKE)
        assertEquals(0f, WordMotion.at(style, 1000L until 1400L, 900).highlight, 0f)
        assertEquals(1f, WordMotion.at(style, 1000L until 1400L, 1500).highlight, 1e-3f)
    }

    @Test fun plainTextUnitsAreStaggered() {
        val t = WordMotion.timings(TextStyle(wordAnimation = WordAnimation.POP), 4, 3000, null)
        assertEquals(4, t.size)
        assertTrue(t.zipWithNext().all { (a, b) -> b.first > a.first })
        assertEquals(2, WordMotion.keyword(listOf("um", "dois", "incrível", "ok")))
    }
}
