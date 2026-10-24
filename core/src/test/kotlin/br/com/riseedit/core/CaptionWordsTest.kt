package br.com.riseedit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptionWordsTest {
    private val raw = """
        0	1200	 Olá pessoal, tudo bem?
        w	0	300	 Ol
        w	300	500	á
        w	500	900	 pessoal,
        w	900	1000	 tudo
        w	1000	1200	 bem?
        1200	1500	 [Música]
        w	1200	1500	 [Música]
        1500	2600	 Vamos lá
        w	1500	2000	 Vamos
        w	2000	2600	 lá
    """.trimIndent()

    @Test fun parsesSegmentsAndJoinsTokensIntoWords() {
        val s = Subtitles.parse(raw)
        assertEquals(2, s.size)
        assertEquals(listOf("Olá", "pessoal,", "tudo", "bem?"), s[0].words.map { it.text })
        assertEquals(SpeechWord(0, 500, "Olá"), s[0].words[0])
        assertEquals(listOf("Vamos", "lá"), s[1].words.map { it.text })
    }

    @Test fun oldOutputWithoutWordsStillParses() {
        val s = Subtitles.parse("0\t1000\t Oi\n1000\t2000\t Tchau")
        assertEquals(listOf("Oi", "Tchau"), s.map { it.text })
        assertEquals(emptyList<SpeechWord>(), s[0].words)
    }

    @Test fun layersCarryLocalWordsAndHighlight() {
        val clip = Clip(uri = "file:///a.mp4", name = "a", sourceDurationMs = 10_000)
        val mapped = Subtitles.mapClip(Subtitles.parse(raw), clip, 5_000)
        val layers = Subtitles.layers(mapped, 20_000)
        val first = layers[0]
        assertEquals(5_000, first.startMs)
        assertEquals(CaptionWord(0, 500, "Olá"), first.words[0])
        assertEquals(CaptionWords.HIGHLIGHT, first.highlightColor)
        assertEquals(4..11, CaptionWords.activeRange(first, 600)) // "pessoal,"
        assertNull(CaptionWords.activeRange(first.copy(highlightColor = 0), 600))
    }

    @Test fun retimeStaysBetweenNeighbors() {
        val layer = TextLayer(
            text = "um dois três", startMs = 0, endMs = 3000,
            words = listOf(CaptionWord(0, 1000, "um"), CaptionWord(1000, 2000, "dois"), CaptionWord(2000, 3000, "três")),
            highlightColor = CaptionWords.HIGHLIGHT
        )
        val moved = CaptionWords.retime(layer, 1, 500, 2500)
        assertEquals(CaptionWord(1000, 2000, "dois"), moved.words[1])
        val inner = CaptionWords.retime(layer, 1, 1200, 1800)
        assertEquals(CaptionWord(1200, 1800, "dois"), inner.words[1])
    }
}
