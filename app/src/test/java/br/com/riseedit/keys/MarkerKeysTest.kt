package br.com.riseedit.keys

import br.com.riseedit.core.shortcuts.KeyChord
import br.com.riseedit.core.shortcuts.RiseAction
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.24.0: M comes from premiere-marce.kys (cmd.set.marker), not from a Rise default. */
class MarkerKeysTest {
    private val keymap = AppKeymap.fromText(File("src/main/assets/keymaps/premiere-marce.kys").readText())

    @Test fun premiereMarkerChords() {
        assertEquals(RiseAction.MARKER, keymap.action(KeyChord("M")))
        assertEquals(RiseAction.MARKER_NEXT, keymap.action(KeyChord("M", shift = true)))
        assertEquals(RiseAction.MARKER_PREV, keymap.action(KeyChord("M", ctrl = true, shift = true)))
        assertEquals("cmd.set.marker", keymap.bindings.first { it.chord == KeyChord("M") }.premiereCommand)
    }
}
