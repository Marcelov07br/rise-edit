package br.com.riseedit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLookTest {
    private val style = TextStyle(animationMs = 400)

    @Test fun fadeRampsInAndOutAndIsHiddenOutsideTheLayer() {
        assertEquals(0f, TextMotion.at(style, -1, 3000).alpha, 0f)
        assertEquals(0f, TextMotion.at(style, 0, 3000).alpha, 1e-4f)
        assertEquals(0.875f, TextMotion.at(style, 200, 3000).alpha, 1e-4f)
        assertEquals(1f, TextMotion.at(style, 1500, 3000).alpha, 0f)
        assertEquals(0.875f, /* 0.25.0 ease-out cubic */ TextMotion.at(style, 2800, 3000).alpha, 1e-4f)
        assertEquals(0f, TextMotion.at(style, 3000, 3000).alpha, 0f)
    }

    @Test fun shortLayersSplitTheAnimationTime() {
        val frame = TextMotion.at(style, 100, 200)
        assertEquals(1f, frame.alpha, 1e-4f)
    }

    @Test fun slideStartsBelowAndSettles() {
        val slide = style.copy(
            animationIn = TextAnimation.SLIDE_UP,
            animationOut = TextAnimation.NONE
        )
        assertTrue(TextMotion.at(slide, 0, 3000).offsetY < -0.1f)
        assertEquals(0f, TextMotion.at(slide, 400, 3000).offsetY, 1e-5f)
        assertEquals(1f, TextMotion.at(slide, 2999, 3000).alpha, 0f)
    }

    @Test fun popOvershootIsClampedAndEndsAtOne() {
        val values = (0..100).map { TextMotion.popScale(it / 100f) }
        assertEquals(0.6f, values.first(), 1e-4f)
        assertEquals(1f, values.last(), 1e-4f)
        assertTrue(values.max() <= 1.08f)
        assertTrue(values.max() > 1f)
    }

    @Test fun typewriterRevealsCharacters() {
        val typing = style.copy(
            animationIn = TextAnimation.TYPEWRITER,
            animationOut = TextAnimation.NONE
        )
        assertEquals(0.5f, TextMotion.at(typing, 200, 3000).reveal, 1e-4f)
        assertEquals(1f, TextMotion.at(typing, 1000, 3000).reveal, 0f)
    }

    @Test fun legacyMappingAndPresetsKeepFont() {
        assertEquals(TextAnimation.NONE, TextStyle.legacy(false).animationIn)
        assertEquals(250L, TextStyle.legacy(true).animationMs)
        val base = TextStyle(font = "bebas", strokeWidth = 0.2f)
        TextPresets.styles.forEach { assertEquals("bebas", it.apply(base).font) }
        assertEquals(0f, TextPresets.styles.first().apply(base).strokeWidth, 0f)
        assertTrue(TextTemplates.all.map { it.style.font }.toSet().size >= 5)
    }

    @Test fun masksCoverCenterAndFeatherEdges() {
        assertEquals(1f, OverlayLook.mask(OverlayMask.CIRCLE, 0.5f, 0.5f, 0.2f, 1f), 1e-4f)
        assertEquals(0f, OverlayLook.mask(OverlayMask.CIRCLE, 0.02f, 0.02f, 0.2f, 1f), 1e-4f)
        assertEquals(
            0f,
            OverlayLook.mask(OverlayMask.CIRCLE, 0.5f, 0.5f, 0.2f, 1f, invert = true),
            1e-4f
        )
        assertEquals(0f, OverlayLook.mask(OverlayMask.ROUNDED, 0f, 0f, 0.1f, 1f), 1e-4f)
        assertEquals(1f, OverlayLook.mask(OverlayMask.ROUNDED, 0.5f, 0.02f, 0.01f, 1f), 1e-4f)
        assertTrue(OverlayLook.mask(OverlayMask.LINEAR, 0.5f, 0.9f, 0.2f, 1f) > 0.99f)
        assertTrue(OverlayLook.mask(OverlayMask.LINEAR, 0.5f, 0.1f, 0.2f, 1f) < 0.01f)
    }

    @Test fun blendModesKeyOnLumaAndGreen() {
        assertEquals(0f, OverlayLook.blendAlpha(OverlayBlend.LIGHTEN, 0f, 0f, 0f), 1e-4f)
        assertEquals(1f, OverlayLook.blendAlpha(OverlayBlend.LIGHTEN, 1f, 1f, 1f), 1e-4f)
        assertEquals(0f, OverlayLook.blendAlpha(OverlayBlend.DARKEN, 1f, 1f, 1f), 1e-4f)
        assertEquals(0f, OverlayLook.blendAlpha(OverlayBlend.CHROMA_GREEN, 0.1f, 0.9f, 0.1f), 1e-4f)
        assertEquals(1f, OverlayLook.blendAlpha(OverlayBlend.CHROMA_GREEN, 0.9f, 0.3f, 0.2f), 1e-4f)
        assertEquals(1f, OverlayLook.blendAlpha(OverlayBlend.NORMAL, 0f, 1f, 0f), 0f)
    }
}
