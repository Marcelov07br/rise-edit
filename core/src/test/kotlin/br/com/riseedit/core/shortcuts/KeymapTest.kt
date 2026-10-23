package br.com.riseedit.core.shortcuts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeymapTest {
    private val sample = """
        <PremiereData><shortcuts>
        <context.global Version="1">
          <item.1 Version="1"><virtualkey>2147483738</virtualkey><modifier.ctrl>false</modifier.ctrl>
            <modifier.alt>false</modifier.alt><modifier.shift>false</modifier.shift>
            <commandname>cmd.sequence.rippletrimpreviousedittoplayhead</commandname></item.1>
          <item.2 Version="1"><virtualkey>2147483716</virtualkey><modifier.ctrl>false</modifier.ctrl>
            <modifier.alt>false</modifier.alt><modifier.shift>false</modifier.shift>
            <commandname>cmd.sequence.rippletrimnextedittoplayhead</commandname></item.2>
          <item.3 Version="1"><virtualkey>43</virtualkey><modifier.ctrl>false</modifier.ctrl>
            <modifier.alt>false</modifier.alt><modifier.shift>true</modifier.shift>
            <commandname>cmd.transport.step.forward.five</commandname></item.3>
          <item.4 Version="1"><virtualkey>2147483717</virtualkey><modifier.ctrl>true</modifier.ctrl>
            <modifier.alt>false</modifier.alt><modifier.shift>false</modifier.shift>
            <commandname>cmd.edit.editoriginal</commandname></item.4>
          <item.5 Version="1"><virtualkey>2147483693</virtualkey><modifier.ctrl>false</modifier.ctrl>
            <modifier.alt>false</modifier.alt><modifier.shift>false</modifier.shift>
            <commandname>cmd.zoom.out</commandname></item.5>
          <item.6 Version="1"><commandname>cmd.unassigned</commandname></item.6>
          <item.7 Version="1"><virtualkey>35</virtualkey><modifier.ctrl>false</modifier.ctrl>
            <modifier.alt>false</modifier.alt><modifier.shift>false</modifier.shift>
            <commandname>cmd.edit.clear</commandname></item.7>
        </context.global>
        <context.timeline Version="1">
          <item.8 Version="1"><virtualkey>2</virtualkey><modifier.ctrl>false</modifier.ctrl>
            <modifier.alt>false</modifier.alt><modifier.shift>false</modifier.shift>
            <commandname>cmd.edit.clear</commandname></item.8>
        </context.timeline>
        </shortcuts></PremiereData>
    """.trimIndent()

    @Test fun parsesKeysAndModifiers() {
        val b = KysParser.parse(sample)
        assertEquals(7, b.size)
        assertEquals(KeyChord("Z"), b[0].chord)
        assertEquals(KeyChord("D"), b[1].chord)
        assertEquals(KeyChord("Right", shift = true), b[2].chord)
        assertEquals(KeyChord("E", ctrl = true), b[3].chord)
        assertEquals(KeyChord("-"), b[4].chord)
        assertEquals(KeyChord("Delete"), b[5].chord)
        assertEquals("timeline", b[6].context)
        assertEquals(KeyChord("Backspace"), b[6].chord)
    }

    @Test fun zAndDAreRippleTrims() {
        val map = Keymaps.fromKys(Keymaps.PREMIERE_MARCE, KysParser.parse(sample))
        assertEquals(RiseAction.RIPPLE_TRIM_START, map.action(KeyChord("Z")))
        assertEquals(RiseAction.RIPPLE_TRIM_END, map.action(KeyChord("D")))
        assertEquals(RiseAction.FRAMES_FORWARD_5, map.action(KeyChord("Right", shift = true)))
        // Premiere's Ctrl+E (edit original) has no Rise equivalent → free for Export.
        assertEquals(RiseAction.EXPORT, map.action(KeyChord("E", ctrl = true)))
        assertEquals(RiseAction.DELETE, map.action(KeyChord("Backspace")))
        assertTrue(map.conflicts().isEmpty())
    }

    @Test fun unsupportedListsUnknownCommands() {
        val u = Keymaps.unsupported(KysParser.parse(sample))
        assertEquals(listOf("cmd.edit.editoriginal"), u.map { it.command })
    }

    @Test fun keyNames() {
        assertEquals("Space", KysParser.keyName(1))
        assertEquals("F5", KysParser.keyName(0x0B))
        assertEquals("Ç", KysParser.keyName(0x800000C7))
        assertEquals("Num1", KysParser.keyName(0x40000031))
        assertEquals("4", KysParser.keyName('4'.code.toLong()))
        assertNull(KysParser.keyName(0x99))
    }

    /** The bundled preset (excerpt of Marce's real .kys) — Z/D and no conflicts. */
    @Test fun bundledMarcePreset() {
        val f = File("../app/src/main/assets/keymaps/premiere-marce.kys")
        if (!f.exists()) return
        val parsed = KysParser.parse(f.readText())
        assertTrue(parsed.size > 300)
        val map = Keymaps.fromKys(Keymaps.PREMIERE_MARCE, parsed)
        assertTrue(map.conflicts().isEmpty())
        assertEquals(RiseAction.RIPPLE_TRIM_START, map.action(KeyChord("Z")))
        assertEquals(RiseAction.RIPPLE_TRIM_END, map.action(KeyChord("D")))
        assertEquals(RiseAction.PLAY_PAUSE, map.action(KeyChord("Space")))
        assertEquals(RiseAction.SPLIT, map.action(KeyChord("C")))
        assertEquals(RiseAction.UNDO, map.action(KeyChord("Z", ctrl = true)))
        assertEquals(RiseAction.EXPORT, map.action(KeyChord("M", ctrl = true)))
        assertEquals(RiseAction.EXPORT, map.action(KeyChord("E", ctrl = true)))
        System.getenv("RISE_KEYMAP_REPORT")?.let { out ->
            File(out).printWriter().use { w ->
                map.bindings.forEach { w.println("${it.chord.label}\t${it.action.label}\t${it.premiereCommand ?: "(padrão Rise)"}") }
                w.println("--- sem equivalente")
                Keymaps.unsupported(parsed).forEach { w.println("${it.chord.label}\t${it.command}") }
            }
        }
    }
}
