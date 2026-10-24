package br.com.riseedit.core

/** Whisper ggml models; no size cap — the user picks, the file is downloaded on demand. */
/**
 * whisper.cpp ggml models (huggingface.co/ggerganov/whisper.cpp). [sha256]/[bytes] = the LFS
 * object of each file on that repo (checked after download). 0.24.0: q5_1 base/small first.
 */
enum class WhisperModel(
    val label: String,
    val file: String,
    val approxMb: Int,
    val bytes: Long,
    val sha256: String
) {
    BASE_Q5("Base q5", "ggml-base-q5_1.bin", 60, 59_707_625, "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"),
    SMALL_Q5("Small q5", "ggml-small-q5_1.bin", 190, 190_085_487, "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb"),
    TINY("Tiny", "ggml-tiny.bin", 78, 77_691_713, "be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21"),
    BASE("Base", "ggml-base.bin", 148, 147_951_465, "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe"),
    SMALL("Small", "ggml-small.bin", 488, 487_601_967, "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b"),
    MEDIUM("Medium", "ggml-medium.bin", 1534, 1_533_763_059, "6c14d5adee5f86394037b4e4e8b59f1673b6cee10e3cf0b11bbdbee79c156208"),
    LARGE_TURBO("Large v3 Turbo", "ggml-large-v3-turbo.bin", 1625, 1_624_555_275, "1fc70f774d38eb169993ac391eea357ef47c88757ef72ee5943879b7e8e2bc69"),
    LARGE("Large v3", "ggml-large-v3.bin", 3095, 3_095_033_483, "64d182b440b98d5203c4f9bd541544d84c605196c4f7b845dfa11fb23594d1e2");

    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"

    companion object {
        val DEFAULT = BASE_Q5
    }
}

/** One spoken word; times like the segment's (source ms, then timeline ms after mapping). */
data class SpeechWord(val startMs: Long, val endMs: Long, val text: String)

data class SpeechSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<SpeechWord> = emptyList()
)

object Subtitles {
    /**
     * Parses the JNI output: "t0Ms\tt1Ms\ttext" segment lines, each followed (0.24.0) by
     * "w\tt0Ms\tt1Ms\tpiece" token lines; pieces starting with a space begin a new word.
     */
    fun parse(raw: String, shiftMs: Long = 0): List<SpeechSegment> {
        val out = mutableListOf<SpeechSegment>()
        var current: SpeechSegment? = null
        var skip = false
        val words = mutableListOf<SpeechWord>()
        fun flush() {
            current?.let { out += it.copy(words = words.toList()) }
            current = null
            words.clear()
        }
        for (line in raw.lineSequence()) {
            val parts = line.split('\t', limit = 4)
            if (parts.size == 4 && parts[0] == "w") {
                if (current == null || skip) continue
                val t0 = parts[1].toLongOrNull() ?: continue
                val t1 = parts[2].toLongOrNull() ?: continue
                val piece = parts[3]
                if (piece.isBlank()) continue
                val startsWord = piece.startsWith(" ") || words.isEmpty()
                val clean = piece.trim()
                if (startsWord) {
                    words += SpeechWord(t0 + shiftMs, maxOf(t1, t0 + 1) + shiftMs, clean)
                } else {
                    val last = words.removeAt(words.lastIndex)
                    words += last.copy(endMs = maxOf(last.endMs, t1 + shiftMs), text = last.text + clean)
                }
                continue
            }
            val seg = line.split('\t', limit = 3)
            if (seg.size < 3) continue
            val start = seg[0].toLongOrNull() ?: continue
            val end = seg[1].toLongOrNull() ?: continue
            flush()
            val text = seg[2].trim()
            skip = text.isEmpty() || text.startsWith("[") || text.startsWith("(")
            if (!skip) current = SpeechSegment(start + shiftMs, maxOf(end, start + 1) + shiftMs, text)
        }
        flush()
        return out
    }

    /** Maps segments of one clip's source audio (ms since clip.startMs) onto the timeline. */
    fun mapClip(
        segments: List<SpeechSegment>,
        clip: Clip,
        clipOffsetMs: Long
    ): List<SpeechSegment> = segments.mapNotNull { s ->
        val a = clip.outputAt(clip.startMs + s.startMs) ?: return@mapNotNull null
        val b =
            clip.outputAt((clip.startMs + s.endMs).coerceAtMost(clip.endMs)) ?: clip.durationMs
        if (b <= a) return@mapNotNull null
        val words = s.words.mapNotNull { w ->
            val wa = clip.outputAt((clip.startMs + w.startMs).coerceAtMost(clip.endMs)) ?: return@mapNotNull null
            val wb = clip.outputAt((clip.startMs + w.endMs).coerceAtMost(clip.endMs)) ?: clip.durationMs
            SpeechWord(clipOffsetMs + wa, clipOffsetMs + maxOf(wb, wa + 1), w.text)
        }
        SpeechSegment(clipOffsetMs + a, clipOffsetMs + b, s.text, words)
    }

    /** Shorts-style captions: centered, low, one segment per layer, no overlaps. */
    fun layers(segments: List<SpeechSegment>, durationMs: Long): List<TextLayer> {
        val sorted = segments.sortedBy { it.startMs }
        return sorted.mapIndexedNotNull { i, s ->
            val next = sorted.getOrNull(i + 1)?.startMs ?: durationMs
            val start = s.startMs.coerceIn(0, maxOf(0, durationMs - 1))
            val end = minOf(s.endMs, next, durationMs)
            if (end <= start) {
                null
            } else {
                TextLayer(
                    text = s.text,
                    startMs = start,
                    endMs = end,
                    y = -0.7f,
                    size = 0.8f,
                    animated = false,
                    words = CaptionWords.fromSpeech(s.words, start, end),
                    highlightColor = if (s.words.isEmpty()) 0 else CaptionWords.HIGHLIGHT
                )
            }
        }
    }
}

/** Inverse of [Clip.sourceAt]: timeline position (ms since clip start) of a source time. */
fun Clip.outputAt(sourceMs: Long): Long? {
    if (sourceMs !in startMs..endMs) return null
    var lo = 0L
    var hi = durationMs
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (sourceAt(mid) < sourceMs) lo = mid + 1 else hi = mid
    }
    return lo
}

/** One word of a caption layer: times are local (ms since the layer starts). */
data class CaptionWord(val startMs: Long, val endMs: Long, val text: String)

/** 0.24.0 word-level captions: active-word highlight + editable word times. */
object CaptionWords {
    const val HIGHLIGHT = 0xFFFFCC00.toInt()

    fun fromSpeech(words: List<SpeechWord>, layerStart: Long, layerEnd: Long): List<CaptionWord> {
        val span = layerEnd - layerStart
        return words.map {
            val a = (it.startMs - layerStart).coerceIn(0, span - 1)
            CaptionWord(a, (it.endMs - layerStart).coerceIn(a + 1, span), it.text)
        }
    }

    /** Char ranges of each word inside [text], matched in order (case-insensitive). */
    fun ranges(text: String, words: List<CaptionWord>): List<IntRange?> {
        var from = 0
        return words.map { w ->
            val i = text.indexOf(w.text, from, ignoreCase = true)
            if (i < 0 || w.text.isEmpty()) {
                null
            } else {
                from = i + w.text.length
                i until from
            }
        }
    }

    fun activeIndex(words: List<CaptionWord>, localMs: Long): Int =
        words.indexOfLast { localMs >= it.startMs && localMs < it.endMs }

    /** Char range to highlight at [localMs] (null = none). */
    fun activeRange(layer: TextLayer, localMs: Long): IntRange? {
        if (layer.highlightColor == 0 || layer.words.isEmpty()) return null
        val i = activeIndex(layer.words, localMs)
        if (i < 0) return null
        return ranges(layer.text, layer.words)[i]
    }

    /**
     * Moves word [index] to [startMs, endMs) (local), clamped between its neighbors and the
     * layer, so the order never flips and words never overlap.
     */
    fun retime(layer: TextLayer, index: Int, startMs: Long, endMs: Long): TextLayer {
        val words = layer.words
        if (index !in words.indices) return layer
        val span = layer.endMs - layer.startMs
        val lo = words.getOrNull(index - 1)?.endMs ?: 0L
        val hi = words.getOrNull(index + 1)?.startMs ?: span
        val a = startMs.coerceIn(lo, maxOf(lo, hi - 1))
        val b = endMs.coerceIn(a + 1, maxOf(a + 1, hi))
        return layer.copy(words = words.toMutableList().apply { set(index, words[index].copy(startMs = a, endMs = b)) })
    }
}
