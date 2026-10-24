package br.com.riseedit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionFlowTest {
    private fun at(kind: FlowKind, ms: Long, accent: Boolean = false, index: Int = 0, end: Long = 10_000, active: Boolean = true) =
        FlowMotion.at(kind, 1000, end, 1000 + ms, index, accent, active)

    @Test fun rolesFromKeywordsAndHeuristics() {
        assertEquals(WordRole.FILLER, WordRoles.classify("de"))
        assertEquals(WordRole.EMOTIVE, WordRoles.classify("Amor!"))
        assertEquals(WordRole.THEMATIC, WordRoles.classify("vida,"))
        assertEquals(WordRole.EMPHASIS, WordRoles.classify("nunca"))
        assertEquals(WordRole.EMPHASIS, WordRoles.classify("PARE"))
        assertEquals(WordRole.EMPHASIS, WordRoles.classify("100%"))
        assertEquals(WordRole.EMPHASIS, WordRoles.classify("vamos!"))
        assertEquals(WordRole.NORMAL, WordRoles.classify("casa"))
    }

    @Test fun overridesBeatAutomaticRule() {
        val words = listOf("eu", "amo", "casa")
        assertEquals(listOf(false, true, false), WordRoles.accents(FlowKind.CLUSTER, words, emptyMap()))
        assertEquals(listOf(false, false, true), WordRoles.accents(FlowKind.CLUSTER, words, mapOf(1 to false, 2 to true)))
        // Cinema accents thematic words, cluster does not.
        assertTrue(WordRoles.accent(FlowKind.CINEMA, WordRole.THEMATIC))
        assertFalse(WordRoles.accent(FlowKind.CLUSTER, WordRole.THEMATIC))
    }

    @Test fun groupsSplitAtPunctuationGapsAndMaxWords() {
        val words = listOf("um", "dois", "três.", "quatro", "cinco", "seis", "sete")
        val times = listOf(0L..100, 100L..200, 200L..300, 300L..400, 900L..1000, 1000L..1100, 1100L..1200)
        assertEquals(listOf(0..1, 2..2, 3..3, 4..5, 6..6), FlowGroups.groups(words, times, 2))
        val g = FlowGroups.groups(words, times, 5)
        assertEquals(listOf(0..2, 3..3, 4..6), g)
        assertEquals(-1, FlowGroups.current(g, times, -5))
        assertEquals(1, FlowGroups.current(g, times, 500))
        assertEquals(2, FlowGroups.current(g, times, 950))
    }

    @Test fun clusterPopsWithOvershootAndVerticalBlur() {
        assertEquals(FlowFrame.HIDDEN, at(FlowKind.CLUSTER, -1))
        val start = at(FlowKind.CLUSTER, 0)
        assertEquals(0.6f, start.scale, 1e-4f)
        assertEquals(0.15f, start.dy, 1e-4f)
        assertEquals(15f, start.blurY, 1e-4f)
        assertEquals(0f, start.blurX, 0f)
        assertEquals(1.15f, at(FlowKind.CLUSTER, (FlowMotion.CLUSTER_IN * 0.6).toLong()).scale, 0.01f)
        val done = at(FlowKind.CLUSTER, FlowMotion.CLUSTER_IN)
        assertEquals(1f, done.scale, 1e-4f)
        assertEquals(0f, done.blurY, 1e-4f)
        assertEquals(0f, done.dy, 1e-4f)
        // Whole cluster cuts at the phrase end.
        assertEquals(FlowFrame.HIDDEN, at(FlowKind.CLUSTER, 9000, end = 1500))
    }

    @Test fun hyperIsFastSpringAndHardCut() {
        assertEquals(0.7f, at(FlowKind.HYPER, 0).scale, 1e-4f)
        assertEquals(1f, at(FlowKind.HYPER, 0).alpha, 0f)
        assertEquals(1f, at(FlowKind.HYPER, FlowMotion.HYPER_IN).scale, 1e-4f)
        assertEquals(1f, at(FlowKind.HYPER, 30).highlight, 0f)
        assertEquals(0f, at(FlowKind.HYPER, 30, active = false).highlight, 0f)
        assertEquals(FlowFrame.HIDDEN, at(FlowKind.HYPER, 600, end = 1500))
        assertEquals(0.85f, FlowMotion.hyperWidth(true, WordRole.EMPHASIS), 0f)
        assertEquals(0.2f, FlowMotion.hyperWidth(false, WordRole.FILLER), 0f)
    }

    @Test fun cinemaFadesWithGaussianBlurAndCrossfades() {
        val s = at(FlowKind.CINEMA, 0)
        assertEquals(0f, s.alpha, 1e-4f)
        assertEquals(10f, s.gauss, 1e-4f)
        assertEquals(0.2f, s.dy, 1e-4f)
        val mid = at(FlowKind.CINEMA, 140)
        assertEquals(0.875f, mid.alpha, 1e-3f)
        assertEquals(1.25f, mid.gauss, 1e-3f)
        assertEquals(1f, at(FlowKind.CINEMA, FlowMotion.CINEMA_IN).alpha, 1e-4f)
        // 150 ms crossfade after the group end, then gone.
        val out = FlowMotion.at(FlowKind.CINEMA, 0, 1000, 1075, 0, false, false)
        assertTrue(out.alpha in 0.05f..0.2f)
        assertEquals(FlowFrame.HIDDEN, FlowMotion.at(FlowKind.CINEMA, 0, 1000, 1150, 0, false, false))
    }

    @Test fun speedSlidesFromAlternatingSidesWithHorizontalBlur() {
        val left = at(FlowKind.SPEED, 0, index = 0)
        val right = at(FlowKind.SPEED, 0, index = 1)
        assertEquals(-0.5f, left.dx, 1e-4f)
        assertEquals(0.5f, right.dx, 1e-4f)
        assertEquals(36f, left.blurX, 1e-4f)
        assertEquals(0f, left.blurY, 0f)
        assertEquals(1.05f, left.scale, 0f)
        assertEquals(1f, at(FlowKind.SPEED, 0, active = false).scale, 0f)
        // Expo-out: 1 - 2^-5 of the way at half time.
        assertEquals(-0.5f / 32, at(FlowKind.SPEED, 100).dx, 1e-4f)
        assertEquals(0f, at(FlowKind.SPEED, FlowMotion.SPEED_IN).blurX, 1e-4f)
        // Slides off with blur toward the other side.
        val off = FlowMotion.at(FlowKind.SPEED, 0, 1000, 1080, 0, false, false)
        assertTrue(off.dx > 0.3f)
        assertTrue(off.blurX > 20f)
        assertEquals(FlowFrame.HIDDEN, FlowMotion.at(FlowKind.SPEED, 0, 1000, 1000 + FlowMotion.SPEED_OUT, 0, false, false))
    }

    @Test fun speedTrackingSnapsWithSlightOvershootOnEmphasis() {
        assertNull(at(FlowKind.SPEED, 50).tracking)
        assertEquals(0.6f, at(FlowKind.SPEED, 0, accent = true).tracking!!, 1e-4f)
        val samples = (0..FlowMotion.SPEED_TRACK step 10).map { at(FlowKind.SPEED, it, accent = true).tracking!! }
        assertTrue("overshoots below zero", samples.min() < 0f)
        assertTrue("slight overshoot", samples.min() > -0.1f)
        assertEquals(0f, at(FlowKind.SPEED, FlowMotion.SPEED_TRACK, accent = true).tracking!!, 1e-4f)
    }

    @Test fun easingsHitEnds() {
        assertEquals(0f, FlowMotion.outExpo(0f), 0f)
        assertEquals(1f, FlowMotion.outExpo(1f), 0f)
        assertEquals(1f - 1f / 32, FlowMotion.outExpo(0.5f), 1e-5f)
        assertEquals(0.6f, FlowMotion.overshoot(0f, 0.6f, 1.15f), 0f)
        assertEquals(1f, FlowMotion.overshoot(1f, 0.6f, 1.15f), 1e-5f)
        assertEquals(0L, FlowMotion.tail(FlowKind.CLUSTER))
        assertEquals(FlowMotion.CINEMA_OUT, FlowMotion.tail(FlowKind.CINEMA))
    }

    @Test fun presetsExistWithFonts() {
        val names = TextLooks.all.takeLast(4).map { it.name }
        assertEquals(listOf("Cluster", "Hiper-escala", "Serifa cinema", "Velocidade"), names)
        val cluster = TextLooks.all.first { it.name == "Cluster" }.style
        assertEquals("caveat", cluster.secondaryFont)
        assertEquals(900, cluster.weight)
        assertEquals(FlowKind.SPEED, TextLooks.all.last().style.flow)
    }

    @Test fun lookScopeDoesNotCopyWordOverrides() {
        val a = TextLayer(text = "eu amo casa", fontOverrides = mapOf(2 to true))
        val b = TextLayer(text = "outra frase", fontOverrides = mapOf(0 to true))
        assertEquals(mapOf(0 to true), CaptionScope.withLook(b, a).fontOverrides)
    }
}
