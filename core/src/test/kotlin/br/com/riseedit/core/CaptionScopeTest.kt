package br.com.riseedit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionScopeTest {
    private fun cap(id: String, group: String? = "g", start: Long = 0) =
        TextLayer(id = id, text = id, startMs = start, endMs = start + 1000, captionGroup = group)

    private val texts = listOf(
        cap("a"), cap("b", start = 1000), cap("c", group = "other"),
        TextLayer(id = "plain", text = "Título")
    )

    @Test fun allAppliesLookToTheSameTrackOnly() {
        val edited = texts[0].copy(color = 0xFFFF0000.toInt(), style = TextStyle(font = "fraunces"), y = 0.4f)
        val out = CaptionScope.apply(texts, edited, LookScope.ALL)
        assertEquals("fraunces", out[1].style.font)
        assertEquals(0.4f, out[1].y, 0f)
        assertEquals("b", out[1].text)
        assertEquals(1000L, out[1].startMs)
        assertEquals("default", out[2].style.font)
        assertEquals("default", out[3].style.font)
        assertEquals(2, CaptionScope.count(texts, edited))
    }

    @Test fun onlyThisAndPlainTextStayIndividual() {
        val one = texts[0].copy(style = TextStyle(font = "lora"))
        assertEquals("default", CaptionScope.apply(texts, one, LookScope.ONLY_THIS)[1].style.font)
        val plain = texts[3].copy(style = TextStyle(font = "lora"))
        val out = CaptionScope.apply(texts, plain, LookScope.ALL)
        assertEquals(listOf("default", "default", "default", "lora"), out.map { it.style.font })
    }

    @Test fun newLayerIsAppended() {
        val fresh = TextLayer(id = "n", text = "novo")
        assertEquals(5, CaptionScope.apply(texts, fresh, LookScope.ALL).size)
    }

    @Test fun looksAreDistinctAndKeepHighlightOffPlainText() {
        assertTrue(TextLooks.all.size >= 14)
        assertEquals(TextLooks.all.size, TextLooks.all.map { it.name }.toSet().size)
        val look = TextLooks.all.first { it.name == "Karaokê" }
        assertEquals(0, look.applyTo(texts[3]).highlightColor)
        assertEquals(look.highlightColor, look.applyTo(texts[0].copy(words = listOf())).highlightColor)
    }
}
