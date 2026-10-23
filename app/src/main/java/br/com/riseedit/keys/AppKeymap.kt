package br.com.riseedit.keys

import android.content.Context
import android.view.KeyEvent
import br.com.riseedit.core.shortcuts.KeyChord
import br.com.riseedit.core.shortcuts.Keymap
import br.com.riseedit.core.shortcuts.Keymaps
import br.com.riseedit.core.shortcuts.KysParser

/**
 * The one place the app gets its keymap: the "Premiere do Marce" preset, parsed at runtime from the
 * bundled .kys excerpt (assets/keymaps/premiere-marce.kys) plus the Rise defaults on free chords.
 */
object AppKeymap {
    @Volatile private var cached: Keymap? = null

    fun get(context: Context): Keymap = cached ?: synchronized(this) {
        cached ?: load(context).also { cached = it }
    }

    /** For previews/tests that have assets but no cache. */
    fun fromText(kys: String): Keymap = Keymaps.fromKys(Keymaps.PREMIERE_MARCE, KysParser.parse(kys))

    private fun load(context: Context): Keymap = runCatching {
        context.assets.open("keymaps/premiere-marce.kys").bufferedReader().use { fromText(it.readText()) }
    }.getOrElse { Keymaps.defaults() }

    private val SPECIAL = mapOf(
        KeyEvent.KEYCODE_SPACE to "Space", KeyEvent.KEYCODE_DEL to "Backspace",
        KeyEvent.KEYCODE_FORWARD_DEL to "Delete", KeyEvent.KEYCODE_ENTER to "Enter",
        KeyEvent.KEYCODE_NUMPAD_ENTER to "Enter", KeyEvent.KEYCODE_ESCAPE to "Esc",
        KeyEvent.KEYCODE_TAB to "Tab", KeyEvent.KEYCODE_MOVE_HOME to "Home",
        KeyEvent.KEYCODE_MOVE_END to "End", KeyEvent.KEYCODE_PAGE_UP to "PageUp",
        KeyEvent.KEYCODE_PAGE_DOWN to "PageDown", KeyEvent.KEYCODE_DPAD_LEFT to "Left",
        KeyEvent.KEYCODE_DPAD_RIGHT to "Right", KeyEvent.KEYCODE_DPAD_UP to "Up",
        KeyEvent.KEYCODE_DPAD_DOWN to "Down", KeyEvent.KEYCODE_NUMPAD_ADD to "Num+",
        KeyEvent.KEYCODE_NUMPAD_SUBTRACT to "Num-"
    )
    private val SYMBOLS = mapOf(
        '=' to KeyEvent.KEYCODE_EQUALS, '-' to KeyEvent.KEYCODE_MINUS, '+' to KeyEvent.KEYCODE_PLUS,
        '\\' to KeyEvent.KEYCODE_BACKSLASH, '[' to KeyEvent.KEYCODE_LEFT_BRACKET,
        ']' to KeyEvent.KEYCODE_RIGHT_BRACKET, ',' to KeyEvent.KEYCODE_COMMA,
        '.' to KeyEvent.KEYCODE_PERIOD, ';' to KeyEvent.KEYCODE_SEMICOLON,
        '\'' to KeyEvent.KEYCODE_APOSTROPHE, '`' to KeyEvent.KEYCODE_GRAVE, '/' to KeyEvent.KEYCODE_SLASH
    )

    /** Android key event → normalized chord (layout-aware for symbols, e.g. ABNT `ç`). */
    fun chord(e: KeyEvent): KeyChord? {
        val code = e.keyCode
        val key = when {
            code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> ('A' + (code - KeyEvent.KEYCODE_A)).toString()
            code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> ('0' + (code - KeyEvent.KEYCODE_0)).toString()
            code in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> "Num${code - KeyEvent.KEYCODE_NUMPAD_0}"
            code in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> "F${code - KeyEvent.KEYCODE_F1 + 1}"
            code in SPECIAL -> SPECIAL.getValue(code)
            else -> e.getUnicodeChar(0).takeIf { it > 0 }?.toChar()?.uppercaseChar()?.toString()
        } ?: return null
        return KeyChord(key, ctrl = e.isCtrlPressed, alt = e.isAltPressed, shift = e.isShiftPressed)
    }

    /** Chord → Android key code for the Meta+/ list; null when Android has no code (e.g. `ç`). */
    fun keyCode(c: KeyChord): Int? {
        val k = c.key
        return when {
            k.length == 1 && k[0] in 'A'..'Z' -> KeyEvent.KEYCODE_A + (k[0] - 'A')
            k.length == 1 && k[0] in '0'..'9' -> KeyEvent.KEYCODE_0 + (k[0] - '0')
            k.startsWith("Num") && k.length == 4 && k[3].isDigit() -> KeyEvent.KEYCODE_NUMPAD_0 + (k[3] - '0')
            k.startsWith("F") && k.length > 1 && k.drop(1).all { it.isDigit() } -> KeyEvent.KEYCODE_F1 + k.drop(1).toInt() - 1
            k.length == 1 -> SYMBOLS[k[0]]
            else -> SPECIAL.entries.firstOrNull { it.value == k }?.key
        }
    }

    fun metaState(c: KeyChord): Int =
        (if (c.ctrl) KeyEvent.META_CTRL_ON else 0) or
            (if (c.alt) KeyEvent.META_ALT_ON else 0) or
            (if (c.shift) KeyEvent.META_SHIFT_ON else 0)
}
