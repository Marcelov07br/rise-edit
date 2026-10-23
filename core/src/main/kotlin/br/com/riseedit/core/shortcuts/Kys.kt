package br.com.riseedit.core.shortcuts

/** One assigned shortcut of a Premiere Pro keyboard-shortcut file (.kys). */
data class KysBinding(val context: String, val chord: KeyChord, val command: String)

/**
 * Parser for Premiere Pro `.kys` (XML). Key codes, as observed in Marce's file:
 * - `0x80000000 | char` for printable keys (letters, digits, `=`, `-`, `\`, `ç`…);
 * - small codes for special keys: 1 Space, 2 Backspace, 4 Enter, 7… F1…, 0x23 Delete,
 *   0x24 Home, 0x25 End, 0x26 PageUp, 0x27 PageDown, 0x2A–0x2D arrows, '0'–'9' top-row digits;
 * - `0x40000030 + n` numpad digits.
 */
object KysParser {
    private val context = Regex("""<context\.([\w.]+) Version="\d+">(.*?)</context\.\1>""", RegexOption.DOT_MATCHES_ALL)
    private val item = Regex("""<item\.\d+ Version="\d+">(.*?)</item\.\d+>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(xml: String): List<KysBinding> = context.findAll(xml).flatMap { ctx ->
        val name = ctx.groupValues[1]
        item.findAll(ctx.groupValues[2]).mapNotNull { m -> binding(name, m.groupValues[1]) }
    }.toList()

    private fun tag(body: String, t: String) = Regex("<$t>(.*?)</$t>").find(body)?.groupValues?.get(1)

    private fun binding(ctx: String, body: String): KysBinding? {
        val vk = tag(body, "virtualkey")?.toLongOrNull() ?: return null
        val cmd = tag(body, "commandname") ?: return null
        val key = keyName(vk) ?: return null
        return KysBinding(
            ctx,
            KeyChord(
                key,
                ctrl = tag(body, "modifier.ctrl") == "true",
                alt = tag(body, "modifier.alt") == "true",
                shift = tag(body, "modifier.shift") == "true"
            ),
            cmd
        )
    }

    private val special = mapOf(
        1L to "Space", 2L to "Backspace", 3L to "Tab", 4L to "Enter", 5L to "Esc",
        0x23L to "Delete", 0x24L to "Home", 0x25L to "End", 0x26L to "PageUp", 0x27L to "PageDown",
        0x2AL to "Left", 0x2BL to "Right", 0x2CL to "Up", 0x2DL to "Down"
    )

    fun keyName(vk: Long): String? {
        if (vk and 0x80000000L != 0L) {
            val c = (vk and 0x7FFFFFFFL).toInt().toChar()
            return if (c == ' ') "Space" else c.uppercaseChar().toString()
        }
        if (vk and 0x40000000L != 0L) {
            val n = (vk and 0xFFL).toInt() - '0'.code
            return if (n in 0..9) "Num$n" else null
        }
        special[vk]?.let { return it }
        if (vk in 7L..18L) return "F${vk - 6}"
        if (vk in '0'.code.toLong()..'9'.code.toLong()) return vk.toInt().toChar().toString()
        return null
    }
}
