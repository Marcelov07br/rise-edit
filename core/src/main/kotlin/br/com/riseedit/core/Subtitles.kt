package br.com.riseedit.core

/** Whisper ggml models; no size cap — the user picks, the file is downloaded on demand. */
enum class WhisperModel(val label: String, val file: String, val approxMb: Int) {
    TINY("Tiny", "ggml-tiny.bin", 75),
    BASE("Base", "ggml-base.bin", 142),
    SMALL("Small", "ggml-small.bin", 466),
    MEDIUM("Medium", "ggml-medium.bin", 1500),
    LARGE_TURBO("Large v3 Turbo", "ggml-large-v3-turbo.bin", 1600),
    LARGE("Large v3", "ggml-large-v3.bin", 3100);

    val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"
}

data class SpeechSegment(val startMs: Long, val endMs: Long, val text: String)

object Subtitles {
    /** Parses the JNI output: "t0Ms\tt1Ms\ttext" lines. */
    fun parse(raw: String, shiftMs: Long = 0): List<SpeechSegment> = raw.lineSequence()
        .mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size < 3) return@mapNotNull null
            val start = parts[0].toLongOrNull() ?: return@mapNotNull null
            val end = parts[1].toLongOrNull() ?: return@mapNotNull null
            val text = parts[2].trim()
            if (text.isEmpty() ||
                text.startsWith("[") ||
                text.startsWith("(")
            ) {
                return@mapNotNull null
            }
            SpeechSegment(start + shiftMs, maxOf(end, start + 1) + shiftMs, text)
        }.toList()

    /** Maps segments of one clip's source audio (ms since clip.startMs) onto the timeline. */
    fun mapClip(
        segments: List<SpeechSegment>,
        clip: Clip,
        clipOffsetMs: Long
    ): List<SpeechSegment> = segments.mapNotNull { s ->
        val a = clip.outputAt(clip.startMs + s.startMs) ?: return@mapNotNull null
        val b =
            clip.outputAt((clip.startMs + s.endMs).coerceAtMost(clip.endMs)) ?: clip.durationMs
        if (b <= a) null else SpeechSegment(clipOffsetMs + a, clipOffsetMs + b, s.text)
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
                    animated = false
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
