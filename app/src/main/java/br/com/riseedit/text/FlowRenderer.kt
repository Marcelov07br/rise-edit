package br.com.riseedit.text

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import br.com.riseedit.core.CapsRule
import br.com.riseedit.core.CaptionWords
import br.com.riseedit.core.FlowFrame
import br.com.riseedit.core.FlowGroups
import br.com.riseedit.core.FlowKind
import br.com.riseedit.core.FlowMotion
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.WordRole
import br.com.riseedit.core.WordRoles
import kotlin.math.ceil
import kotlin.math.max

/**
 * 0.26.0: free word layout for flow presets (cluster, hyper-scale, cinema, speed), drawn on a
 * plain Canvas so the preview and the Media3 export overlay share it (via [TextRenderer.render]).
 * Per word: secondary font, size scale, animated tracking, directional motion blur (multi-tap
 * along X or Y) and gaussian blur (BlurMaskFilter).
 */
internal object FlowRenderer {
    private val TOKEN = Regex("\\S+")
    private const val BLUR_TAPS = 9

    private class Word(
        val text: String,
        val time: LongRange,
        val role: WordRole,
        val accent: Boolean
    )

    private class Placed(val word: Word, val index: Int, val paint: TextPaint, val x: Float, val baseline: Float, val width: Float, val size: Float)

    private val cache = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 24
    }

    private fun words(layer: TextLayer): List<Word> {
        val text = layer.text
        val tokens = TOKEN.findAll(text).map { it.range }.toList()
        if (tokens.isEmpty()) return emptyList()
        val duration = layer.endMs - layer.startMs
        val ranges = if (layer.words.isNotEmpty()) CaptionWords.ranges(text, layer.words) else emptyList()
        val times: List<LongRange> = if (ranges.isNotEmpty()) {
            var last: LongRange? = null
            tokens.map { tk ->
                val i = ranges.indexOfFirst { r -> r != null && r.first <= tk.last && r.last >= tk.first }
                val t = if (i >= 0) layer.words[i].let { it.startMs until it.endMs } else last
                last = t ?: last
                t ?: (0L until 1L)
            }
        } else {
            val step = (duration / tokens.size).coerceAtLeast(1)
            List(tokens.size) { i -> (i * step) until (i + 1) * step }
        }
        val texts = tokens.map { text.substring(it) }
        val kind = layer.style.flow
        val accents = WordRoles.accents(kind, texts, layer.fontOverrides)
        return texts.mapIndexed { i, w ->
            val shown = when {
                layer.style.caps == CapsRule.LOWER -> w.lowercase()
                layer.style.caps == CapsRule.EMPHASIS_UPPER && accents[i] -> w.uppercase()
                else -> w
            }
            Word(shown, times[i], WordRoles.classify(w), accents[i])
        }
    }

    fun render(layer: TextLayer, frameWidth: Int, frameHeight: Int, localMs: Long): Bitmap {
        val style = layer.style
        val kind = style.flow
        val all = words(layer)
        val times = all.map { it.time }
        val groups = FlowGroups.groups(all.map { it.text }, times, style.maxWords)
        val duration = layer.endMs - layer.startMs
        fun groupEnd(g: Int) = if (g + 1 < groups.size) times[groups[g + 1].first].first else duration
        val current = FlowGroups.current(groups, times, localMs)
        // Previous group still animating out (crossfade / slide off), drawn with its own layout.
        val shownGroups = buildList {
            if (current > 0 && localMs - groupEnd(current - 1) < FlowMotion.tail(kind)) add(current - 1)
            if (current >= 0) add(current)
        }
        val fontPx = TextRenderer.FONT_FRACTION * frameHeight
        val layouts = shownGroups.associateWith { g -> layout(groups[g].map { it to all[it] }, layer, frameWidth, fontPx) }
        val frames = shownGroups.associateWith { g ->
            layouts.getValue(g).first.map { p ->
                val w = p.word
                // Slide side follows the landing spot (right half enters from the right) to avoid crossings.
                val side = if (p.x + p.width / 2 > frameWidth / 2f) 1 else 0
                FlowMotion.at(kind, w.time.first, groupEnd(g), localMs, side, w.accent, localMs in w.time, style.blurScale)
            }
        }
        val key = buildString {
            append(layer.text).append('|').append(style.hashCode()).append('|').append(layer.color).append('|')
                .append(layer.highlightColor).append('|').append(layer.fontOverrides.hashCode()).append('|')
                .append(frameWidth).append('x').append(frameHeight).append('|').append(shownGroups)
            frames.values.flatten().forEach { append(quantize(it)) }
        }
        synchronized(cache) { cache[key]?.let { return it } }
        val effects = maxOf(
            style.strokeWidth,
            if (style.glow > 0) style.glow * 0.8f else 0f,
            if (style.shadow > 0) style.shadowBlur + style.shadowDistance else 0f
        )
        val pad = ceil(fontPx * (effects + 0.5f) + 2)
        // Fixed bitmap size over the whole caption (tallest group) so preview/export scaling is stable.
        val contentH = contentHeight(layer, all, groups, frameWidth, fontPx)
        val width = frameWidth.coerceAtLeast(1)
        val height = (contentH + 2 * pad).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for (g in shownGroups) {
            val (placed, h) = layouts.getValue(g)
            val top = pad + (contentH - h) / 2
            placed.forEachIndexed { k, p -> drawWord(canvas, p, frames.getValue(g)[k], layer, top, fontPx, frameWidth, frameHeight) }
        }
        synchronized(cache) { cache[key] = bitmap }
        return bitmap
    }

    private val heights = object : LinkedHashMap<String, Float>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > 64
    }

    private fun contentHeight(layer: TextLayer, all: List<Word>, groups: List<IntRange>, frameWidth: Int, fontPx: Float): Float {
        val key = "${layer.text}|${layer.style.hashCode()}|${layer.fontOverrides.hashCode()}|${layer.words.hashCode()}|$frameWidth|$fontPx"
        synchronized(heights) { heights[key]?.let { return it } }
        val h = groups.maxOfOrNull { r -> layout(r.map { it to all[it] }, layer, frameWidth, fontPx).second } ?: fontPx
        synchronized(heights) { heights[key] = h }
        return h
    }

    /** Bitmap size relative to the frame (width, height), like [TextRenderer.measure]. */
    fun measure(layer: TextLayer, frameWidth: Int, frameHeight: Int): Pair<Float, Float> {
        val b = render(layer, frameWidth, frameHeight, 0L)
        return b.width.toFloat() / frameWidth to b.height.toFloat() / frameHeight
    }

    private fun quantize(f: FlowFrame) = "${(f.alpha * 64).toInt()},${(f.dx * 512).toInt()},${(f.dy * 128).toInt()}," +
        "${(f.scale * 128).toInt()},${(f.highlight * 8).toInt()},${f.blurX.toInt()},${f.blurY.toInt()},${f.gauss.toInt()}," +
        "${f.tracking?.let { (it * 128).toInt() }};"

    private fun paintFor(w: Word, layer: TextLayer, size: Float): TextPaint {
        val s = layer.style
        val secondary = w.accent && s.secondaryFont.isNotEmpty()
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (secondary) {
                TextFonts.typeface(s.secondaryFont, true, false, s.secondaryWeight)
            } else {
                TextFonts.typeface(s.font, s.bold, s.italic, s.weight)
            }
            textSize = size
            letterSpacing = s.letterSpacing
        }
    }

    /** Lines of words (top = 0); returns placed words and the total height. */
    private fun layout(group: List<Pair<Int, Word>>, layer: TextLayer, frameWidth: Int, fontPx: Float): Pair<List<Placed>, Float> {
        val kind = layer.style.flow
        val wrap = frameWidth * 0.86f
        data class Item(val index: Int, val word: Word, val paint: TextPaint, val width: Float, val size: Float)
        val items = group.map { (i, w) ->
            var size = fontPx * FlowMotion.sizeFactor(kind, w.accent, w.role, i)
            var paint = paintFor(w, layer, size)
            if (kind == FlowKind.HYPER) {
                val target = FlowMotion.hyperWidth(w.accent, w.role) * frameWidth
                val measured = paint.measureText(w.text).coerceAtLeast(1f)
                size = (size * target / measured).coerceIn(fontPx * 0.6f, fontPx * 3.4f)
                paint = paintFor(w, layer, size)
            }
            Item(i, w, paint, paint.measureText(w.text), size)
        }
        val lines = ArrayList<MutableList<Item>>()
        for (it in items) {
            val line = lines.lastOrNull()
            val used = line?.sumOf { x -> (x.width + x.size * 0.28f).toDouble() }?.toFloat() ?: 0f
            if (line == null || kind == FlowKind.HYPER || used + it.width > wrap) lines += mutableListOf(it) else line += it
        }
        val placed = ArrayList<Placed>()
        var y = 0f
        val leading = if (kind == FlowKind.CLUSTER) 0.9f else 1.12f
        lines.forEachIndexed { li, line ->
            val lineSize = line.maxOf { it.size }
            val gap = lineSize * 0.26f
            val total = line.sumOf { it.width.toDouble() }.toFloat() + gap * (line.size - 1)
            var x = (frameWidth - total) / 2 + if (kind == FlowKind.CLUSTER) FlowMotion.clusterOffset(li) * fontPx else 0f
            val baseline = y + lineSize * 0.92f
            line.forEach {
                placed += Placed(it.word, it.index, it.paint, x, baseline, it.width, it.size)
                x += it.width + gap
            }
            y += lineSize * leading
        }
        return placed to (y + fontPx * 0.25f)
    }

    @Suppress("LongParameterList")
    private fun drawWord(
        canvas: Canvas,
        p: Placed,
        f: FlowFrame,
        layer: TextLayer,
        top: Float,
        fontPx: Float,
        frameWidth: Int,
        frameHeight: Int
    ) {
        if (f.alpha <= 0.004f) return
        val s = layer.style
        val paint = p.paint
        val hl = layer.highlightColor.takeIf { it != 0 } ?: CaptionWords.HIGHLIGHT
        val base = if (s.flow == FlowKind.CLUSTER && p.word.accent && layer.highlightColor != 0) hl else layer.color
        val fill = blend(base, hl, if (layer.highlightColor != 0) f.highlight else 0f)
        val baseline = top + p.baseline
        val cx = p.x + p.width / 2
        val cy = baseline - p.size * 0.35f
        f.tracking?.let { paint.letterSpacing = it }
        val drawnWidth = paint.measureText(p.word.text)
        val x = cx - drawnWidth / 2
        val px = frameHeight / 1080f
        canvas.save()
        canvas.translate(f.dx * frameWidth, f.dy * fontPx)
        canvas.scale(f.scale, f.scale, cx, cy)
        if (f.gauss > 0.5f) paint.maskFilter = BlurMaskFilter(f.gauss * px, BlurMaskFilter.Blur.NORMAL)
        val blur = max(f.blurX, f.blurY) * px
        if (s.glow > 0) {
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(s.glowColor, f.alpha * s.glow)
            paint.setShadowLayer(fontPx * 0.35f, 0f, 0f, withAlpha(s.glowColor, f.alpha * minOf(1f, s.glow * 3f)))
            canvas.drawText(p.word.text, x, baseline, paint)
        }
        if (s.shadow > 0 && blur < 2f) {
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(fill, f.alpha)
            paint.setShadowLayer(
                max(0.5f, s.shadowBlur * fontPx),
                0f,
                s.shadowDistance * fontPx,
                withAlpha(s.shadowColor, s.shadow * f.alpha)
            )
            canvas.drawText(p.word.text, x, baseline, paint)
        }
        paint.clearShadowLayer()
        if (s.strokeWidth > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = 2 * s.strokeWidth * fontPx
            paint.color = withAlpha(s.strokeColor, f.alpha)
            canvas.drawText(p.word.text, x, baseline, paint)
        }
        paint.style = Paint.Style.FILL
        if (blur >= 2f) {
            // Directional motion blur: taps along the motion axis, weighted to the center.
            val horizontal = f.blurX >= f.blurY
            var weights = 0f
            val w = FloatArray(BLUR_TAPS) { i -> (1f - kotlin.math.abs(i - (BLUR_TAPS - 1) / 2f) / BLUR_TAPS).also { weights += it } }
            for (i in 0 until BLUR_TAPS) {
                val o = (i / (BLUR_TAPS - 1f) - 0.5f) * blur
                paint.color = withAlpha(fill, (f.alpha * w[i] / weights * 2.2f).coerceAtMost(1f))
                canvas.drawText(p.word.text, x + if (horizontal) o else 0f, baseline + if (horizontal) 0f else o, paint)
            }
        } else {
            paint.color = withAlpha(fill, f.alpha)
            canvas.drawText(p.word.text, x, baseline, paint)
        }
        paint.maskFilter = null
        paint.letterSpacing = s.letterSpacing
        canvas.restore()
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt() shl shift
        return ch(24) or ch(16) or ch(8) or ch(0)
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((Color.alpha(color) * alpha).toInt().coerceIn(0, 255) shl 24)
}
