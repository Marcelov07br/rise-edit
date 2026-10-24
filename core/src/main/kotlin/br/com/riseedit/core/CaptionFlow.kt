package br.com.riseedit.core

/** 0.26.0: caption presets with their own layout and motion (TikTok-like references). */
enum class FlowKind(val label: String) {
    NONE("Padrão"),
    CLUSTER("Cluster"),
    HYPER("Hiper-escala"),
    CINEMA("Serifa cinema"),
    SPEED("Velocidade")
}

enum class CapsRule(val label: String) { NONE("Como digitado"), LOWER("minúsculas"), EMPHASIS_UPPER("MAIÚSCULAS no destaque") }

enum class WordRole { FILLER, NORMAL, EMPHASIS, EMOTIVE, THEMATIC }

/** Automatic word roles: small PT/EN keyword lists + heuristics. Manual overrides win. */
object WordRoles {
    private val FILLERS = setOf(
        "a", "o", "as", "os", "e", "é", "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", "um", "uma",
        "que", "pra", "para", "por", "com", "se", "eu", "tu", "ele", "ela", "me", "te", "lhe", "mas", "ou", "ao",
        "the", "a", "an", "of", "to", "in", "on", "and", "or", "is", "it", "i", "you", "at", "for", "so", "but"
    )
    private val EMOTIVE = setOf(
        "amor", "amo", "incrível", "lindo", "linda", "uau", "nossa", "medo", "feliz", "triste", "saudade",
        "sonho", "coração", "perfeito", "perfeita", "demais", "louco", "louca", "chocado", "chocada", "magia",
        "love", "amazing", "wow", "crazy", "dream", "happy", "sad", "heart", "beautiful", "insane", "omg"
    )
    private val EMPHASIS = setOf(
        "nunca", "sempre", "nada", "tudo", "todos", "agora", "hoje", "único", "única", "segredo", "grátis",
        "melhor", "pior", "maior", "erro", "pare", "atenção", "verdade", "rápido", "simples", "fácil", "ninguém",
        "never", "always", "now", "best", "worst", "secret", "free", "stop", "truth", "fast", "everything", "nothing"
    )
    private val THEMATIC = setOf(
        "vida", "tempo", "mundo", "história", "cinema", "luz", "noite", "mar", "céu", "cidade", "futuro", "passado",
        "silêncio", "alma", "arte", "memória", "liberdade", "destino", "caminho", "life", "time", "world", "story",
        "light", "night", "sea", "sky", "city", "future", "soul", "art", "memory"
    )

    fun bare(word: String) = word.lowercase().trim { !it.isLetterOrDigit() }

    fun classify(word: String): WordRole {
        val w = bare(word)
        if (w.isEmpty()) return WordRole.FILLER
        return when {
            w in EMOTIVE -> WordRole.EMOTIVE
            w in THEMATIC -> WordRole.THEMATIC
            w in EMPHASIS -> WordRole.EMPHASIS
            w in FILLERS || w.length <= 2 -> WordRole.FILLER
            word.endsWith("!") || w.any { it.isDigit() } -> WordRole.EMPHASIS
            word.length >= 3 && word.filter { it.isLetter() }.all { it.isUpperCase() } -> WordRole.EMPHASIS
            w.length >= 9 -> WordRole.EMPHASIS
            else -> WordRole.NORMAL
        }
    }

    /** Role used by [kind]: which words take the secondary font / big size / caps. */
    fun accent(kind: FlowKind, role: WordRole): Boolean = when (kind) {
        FlowKind.CLUSTER -> role == WordRole.EMOTIVE || role == WordRole.EMPHASIS
        FlowKind.CINEMA -> role == WordRole.THEMATIC || role == WordRole.EMOTIVE
        FlowKind.HYPER, FlowKind.SPEED -> role == WordRole.EMPHASIS || role == WordRole.EMOTIVE || role == WordRole.THEMATIC
        FlowKind.NONE -> role != WordRole.FILLER && role != WordRole.NORMAL
    }

    /** Per-token accent flags: [overrides] (token index → accent) beat the automatic rule. */
    fun accents(kind: FlowKind, words: List<String>, overrides: Map<Int, Boolean>): List<Boolean> =
        words.mapIndexed { i, w -> overrides[i] ?: accent(kind, classify(w)) }
}

/** Phrase groups: split at sentence punctuation, timing gaps or [maxWords]. */
object FlowGroups {
    const val GAP_MS = 350L

    fun groups(words: List<String>, times: List<LongRange>, maxWords: Int): List<IntRange> {
        if (words.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var start = 0
        for (i in words.indices) {
            val size = i - start + 1
            val phraseEnd = words[i].lastOrNull()?.let { it in ".!?;:," } == true
            val gap = i + 1 < words.size && times[i + 1].first - times[i].last > GAP_MS
            if (i == words.lastIndex || phraseEnd || gap || (maxWords > 0 && size >= maxWords)) {
                out += start..i
                start = i + 1
            }
        }
        return out
    }

    /** Group shown at [localMs]: the last group that started (stays until the next starts). */
    fun current(groups: List<IntRange>, times: List<LongRange>, localMs: Long): Int {
        var g = -1
        groups.forEachIndexed { i, r -> if (localMs >= times[r.first].first) g = i }
        return g
    }
}

/**
 * One word of a flow preset at an instant. [dx] is a fraction of the frame width, [dy] in font
 * sizes, blur amounts in px of a 1080-px-tall frame, [tracking] in em (null = style tracking).
 */
data class FlowFrame(
    val alpha: Float = 1f,
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f,
    val highlight: Float = 0f,
    val blurX: Float = 0f,
    val blurY: Float = 0f,
    val gauss: Float = 0f,
    val tracking: Float? = null
) {
    companion object {
        val HIDDEN = FlowFrame(alpha = 0f)
    }
}

object FlowMotion {
    const val CLUSTER_IN = 140L
    const val HYPER_IN = 90L
    const val CINEMA_IN = 280L
    const val CINEMA_OUT = 150L
    const val SPEED_IN = 200L
    const val SPEED_TRACK = 250L
    const val SPEED_OUT = 160L

    /** 0 → 1 expo ease-out. */
    fun outExpo(t: Float): Float = if (t >= 1f) 1f else if (t <= 0f) 0f else (1 - Math.pow(2.0, -10.0 * t)).toFloat()

    /** Three-key scale a → peak → 1: out-cubic to the peak (60 %), sine settle (no hard linear). */
    fun overshoot(t: Float, from: Float, peak: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return if (c < 0.6f) {
            from + (peak - from) * Easing.outCubic(c / 0.6f)
        } else {
            peak + (1f - peak) * Easing.inOutSine((c - 0.6f) / 0.4f)
        }
    }

    /** How long after the group end words stay drawn (out animation). */
    fun tail(kind: FlowKind): Long = when (kind) {
        FlowKind.CINEMA -> CINEMA_OUT
        FlowKind.SPEED -> SPEED_OUT
        else -> 0L
    }

    /**
     * [start] = word start, [groupEnd] = when its group is replaced (hard cut / out animation),
     * [index] alternates slide sides, [accent] = emphasis word, [active] = being spoken.
     */
    fun at(
        kind: FlowKind,
        start: Long,
        groupEnd: Long,
        localMs: Long,
        index: Int,
        accent: Boolean,
        active: Boolean,
        blurScale: Float = 1f
    ): FlowFrame {
        if (localMs < start) return FlowFrame.HIDDEN
        val since = (localMs - start).toFloat()
        val out = localMs - groupEnd
        if (out >= tail(kind)) return FlowFrame.HIDDEN
        return when (kind) {
            FlowKind.NONE -> FlowFrame()
            FlowKind.CLUSTER -> {
                val t = (since / CLUSTER_IN).coerceIn(0f, 1f)
                val e = Easing.outCubic(t)
                FlowFrame(
                    alpha = minOf(1f, t * 2.5f),
                    dy = 0.15f * (1 - e),
                    scale = overshoot(t, 0.6f, 1.15f),
                    blurY = 15f * (1 - e) * blurScale
                )
            }
            FlowKind.HYPER -> {
                val t = (since / HYPER_IN).coerceIn(0f, 1f)
                FlowFrame(scale = overshoot(t, 0.7f, 1.1f), highlight = if (active) 1f else 0f)
            }
            FlowKind.CINEMA -> {
                val t = (since / CINEMA_IN).coerceIn(0f, 1f)
                val e = Easing.outCubic(t)
                val fade = if (out > 0) 1f - Easing.outCubic(out.toFloat() / CINEMA_OUT) else 1f
                FlowFrame(alpha = e * fade, dy = 0.2f * (1 - e), gauss = 10f * (1 - e) * blurScale)
            }
            FlowKind.SPEED -> {
                val side = if (index % 2 == 0) -1f else 1f
                val t = (since / SPEED_IN).coerceIn(0f, 1f)
                val e = outExpo(t)
                val inDx = side * 0.5f * (1 - e)
                // Blur follows speed: strongest at the start of the slide, gone when it lands.
                var blur = 36f * (1 - e) * blurScale
                var dx = inDx
                var alpha = minOf(1f, t * 4f)
                if (out > 0) {
                    val o = Easing.outCubic(out.toFloat() / SPEED_OUT)
                    dx = -side * 0.6f * o
                    blur = maxOf(blur, 34f * o * blurScale)
                    alpha *= 1f - o * 0.6f
                }
                val tracking = if (accent) {
                    val k = (since / SPEED_TRACK).coerceIn(0f, 1f)
                    0.6f * (1f - Easing.spring(k))
                } else {
                    null
                }
                FlowFrame(
                    alpha = alpha,
                    dx = dx,
                    scale = if (active) 1.05f else 1f,
                    highlight = if (active) 1f else 0f,
                    blurX = blur,
                    tracking = tracking
                )
            }
        }
    }

    /** Relative size of a word in [kind]: hyper-scale targets a width, cluster varies ±. */
    fun sizeFactor(kind: FlowKind, accent: Boolean, role: WordRole, index: Int): Float = when (kind) {
        FlowKind.CLUSTER -> {
            val jitter = floatArrayOf(1f, 1.13f, 0.9f, 1.07f, 0.94f)[index % 5]
            if (accent) jitter * 1.15f else jitter
        }
        FlowKind.HYPER -> if (accent) 2.6f else if (role == WordRole.FILLER) 0.7f else 1.3f
        else -> 1f
    }

    /** Hyper-scale: target word width as a fraction of the frame (filler ~20 %, emphasis ~85 %). */
    fun hyperWidth(accent: Boolean, role: WordRole): Float =
        if (accent) 0.85f else if (role == WordRole.FILLER) 0.2f else 0.45f

    /** Cluster: slightly off-axis line offsets (fraction of font size), deterministic. */
    fun clusterOffset(line: Int): Float = floatArrayOf(0f, -0.35f, 0.3f, -0.2f, 0.25f)[line % 5]
}
