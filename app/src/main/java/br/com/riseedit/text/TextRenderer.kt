package br.com.riseedit.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import br.com.riseedit.core.TextAlign
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.TextStyle
import br.com.riseedit.core.CaptionWords
import br.com.riseedit.core.WordAnimation
import br.com.riseedit.core.WordFrame
import br.com.riseedit.core.WordMotion
import br.com.riseedit.core.WordUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a text layer into a bitmap. Export (Media3 overlay) and the editor preview both call
 * this, so what you see while editing is what gets exported. All metrics scale with the frame
 * height: at size 1 the font is [FONT_FRACTION] of the frame height.
 */
object TextRenderer {
    const val FONT_FRACTION = 42f / 720f
    private const val WRAP_FRACTION = 0.9f
    private const val BG_PAD_X = 0.3f
    private const val BG_PAD_Y = 0.12f

    private data class Key(
        val text: String,
        val style: TextStyle,
        val color: Int,
        val width: Int,
        val height: Int,
        val chars: Int,
        val highlight: IntRange? = null,
        val highlightColor: Int = 0,
        val frames: List<WordFrame>? = null
    )

    private val cache = object : LinkedHashMap<Key, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bitmap>) = size > 12
    }

    /** Size of the rendered text as fractions of the frame (width, height), at scale 1. */
    fun measure(layer: TextLayer, frameAspect: Float): Pair<Float, Float> {
        val height = 720
        val width = (height * frameAspect).toInt().coerceAtLeast(1)
        val plan = plan(layer.text, layer.style, layer.color, width, height, layer.text.length)
        return plan.bitmapWidth.toFloat() / width to plan.bitmapHeight.toFloat() / height
    }

    /** Bitmap for [layer] on a [frameWidth]×[frameHeight] frame showing [reveal] of the chars. */
    fun render(
        layer: TextLayer,
        frameWidth: Int,
        frameHeight: Int,
        reveal: Float = 1f,
        /** 0.24.0: char range of the spoken word, drawn in [TextLayer.highlightColor]. */
        highlight: IntRange? = null,
        /** 0.25.0: ms since the layer starts (word animations, word box). */
        localMs: Long = 0
    ): Bitmap {
        val chars = ceil(layer.text.length * reveal.coerceIn(0f, 1f)).toInt()
        val hl = highlight?.takeIf { layer.highlightColor != 0 && it.first >= 0 && it.last < layer.text.length }
        val style = layer.style
        val perWord = style.wordAnimation != WordAnimation.NONE || style.wordBox ||
            (style.gradientColor != 0 && hl != null)
        val plan = if (perWord) plan(layer.text, style, layer.color, frameWidth, frameHeight, chars) else null
        val units = plan?.let { units(layer, it.layout, localMs) }
        val frames = units?.map { it.frame.quantized() }
        val key = Key(layer.text, style, layer.color, frameWidth, frameHeight, chars, hl, layer.highlightColor, frames)
        synchronized(cache) { cache[key]?.let { return it } }
        val p = plan ?: plan(layer.text, style, layer.color, frameWidth, frameHeight, chars)
        val bitmap = Bitmap.createBitmap(
            p.bitmapWidth,
            p.bitmapHeight,
            Bitmap.Config.ARGB_8888
        )
        if (units != null) {
            drawWords(Canvas(bitmap), p, layer, units, chars)
        } else {
            draw(Canvas(bitmap), p, style, layer.color, hl, layer.highlightColor)
        }
        synchronized(cache) { cache[key] = bitmap }
        return bitmap
    }

    private class Plan(
        val layout: StaticLayout,
        val paint: TextPaint,
        val fontPx: Float,
        val pad: Float,
        val bitmapWidth: Int,
        val bitmapHeight: Int
    )

    private fun plan(
        text: String,
        style: TextStyle,
        color: Int,
        frameWidth: Int,
        frameHeight: Int,
        chars: Int
    ): Plan {
        val fontPx = FONT_FRACTION * frameHeight
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TextFonts.typeface(style.font, style.bold, style.italic)
            textSize = fontPx
            letterSpacing = style.letterSpacing
            this.color = color
        }
        val content = SpannableString(text.ifEmpty { " " })
        if (chars < text.length) {
            content.setSpan(
                ForegroundColorSpan(Color.TRANSPARENT),
                chars,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val wrap = (frameWidth * WRAP_FRACTION).toInt().coerceAtLeast(1)
        val desired = text.ifEmpty { " " }.split('\n')
            .maxOf { ceil(Layout.getDesiredWidth(it, paint)).toInt() }
        val width = min(wrap, max(1, desired))
        val alignment = when (style.align) {
            TextAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
            TextAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, style.lineHeight / 1.2f)
            .setIncludePad(true)
            .build()
        val effects = maxOf(
            style.strokeWidth,
            if (style.glow > 0) style.glow * 0.6f else 0f,
            if (style.shadow > 0) style.shadowBlur + style.shadowDistance else 0f
        )
        val background = if (style.background > 0 || style.wordBox) BG_PAD_X else 0f
        val pad = ceil(fontPx * (effects + background + WordMotion.margin(style)) + 2)
        return Plan(
            layout,
            paint,
            fontPx,
            pad,
            (width + 2 * pad).toInt(),
            (layout.height + 2 * pad).toInt()
        )
    }

    private fun draw(
        canvas: Canvas,
        plan: Plan,
        style: TextStyle,
        color: Int,
        highlight: IntRange? = null,
        highlightColor: Int = 0
    ) {
        val paint = plan.paint
        val layout = plan.layout
        val fontPx = plan.fontPx
        canvas.translate(plan.pad, plan.pad)
        if (style.background > 0) drawBackground(canvas, plan, style)
        if (style.glow > 0) {
            paint.style = Paint.Style.FILL
            paint.color = style.glowColor
            paint.setShadowLayer(style.glow * fontPx * 0.5f, 0f, 0f, style.glowColor)
            repeat(2) { layout.draw(canvas) }
        }
        if (style.shadow > 0) {
            val distance = style.shadowDistance * fontPx * 0.7071f
            val shadow = withAlpha(style.shadowColor, style.shadow)
            paint.style = Paint.Style.FILL
            paint.color = color
            paint.setShadowLayer(max(0.5f, style.shadowBlur * fontPx), distance, distance, shadow)
            layout.draw(canvas)
        }
        paint.clearShadowLayer()
        if (style.strokeWidth > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = 2 * style.strokeWidth * fontPx
            paint.color = style.strokeColor
            layout.draw(canvas)
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        if (style.gradientColor != 0) {
            paint.shader = LinearGradient(0f, 0f, 0f, layout.height.toFloat(), color, style.gradientColor, Shader.TileMode.CLAMP)
        }
        if (highlight == null) {
            layout.draw(canvas)
            paint.shader = null
            return
        }
        // Fill pass only: the spoken word in the highlight color (stroke/glow keep their colors).
        val text = layout.text
        val spanned = SpannableString(text)
        spanned.setSpan(
            ForegroundColorSpan(highlightColor),
            highlight.first,
            (highlight.last + 1).coerceAtMost(text.length),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, layout.width)
            .setAlignment(layout.alignment)
            .setLineSpacing(layout.spacingAdd, layout.spacingMultiplier)
            .setIncludePad(true)
            .build()
            .draw(canvas)
    }

    /** One token (word + trailing punctuation) of the text, with its motion now. */
    private class Unit(val start: Int, val end: Int, val line: Int, val frame: WordFrame, val active: Boolean)

    private fun WordFrame.quantized() = WordFrame(
        (alpha * 64).toInt() / 64f, (dy * 128).toInt() / 128f, (scale * 128).toInt() / 128f, (highlight * 32).toInt() / 32f
    )

    private val TOKEN = Regex("\\S+")

    /** Tokens with timings: caption word times when they match, else staggered. */
    private fun units(layer: TextLayer, layout: StaticLayout, localMs: Long): List<Unit> {
        val text = layer.text
        val tokens = TOKEN.findAll(text).map { it.range }.toList()
        if (tokens.isEmpty()) return emptyList()
        val style = layer.style
        val duration = layer.endMs - layer.startMs
        val ranges = if (layer.words.isNotEmpty()) CaptionWords.ranges(text, layer.words) else emptyList()
        val wordTimes: List<LongRange>? = if (ranges.isNotEmpty()) {
            var last: LongRange? = null
            tokens.map { tk ->
                val i = ranges.indexOfFirst { r -> r != null && r.first <= tk.last && r.last >= tk.first }
                val t = if (i >= 0) layer.words[i].let { it.startMs until it.endMs } else last
                last = t ?: last
                t ?: (0L until 1L)
            }
        } else {
            null
        }
        val lines = tokens.map { layout.getLineForOffset(it.first) }
        val byLine = style.wordUnit == WordUnit.LINE
        val unitKeys = if (byLine) lines else tokens.indices.toList()
        val distinct = unitKeys.distinct()
        val synthetic = WordMotion.timings(style, distinct.size, duration, null)
        fun timeOf(i: Int): LongRange {
            val k = distinct.indexOf(unitKeys[i])
            if (wordTimes == null) return synthetic[k]
            if (!byLine) return wordTimes[i]
            val members = tokens.indices.filter { unitKeys[it] == unitKeys[i] }
            return members.minOf { wordTimes[it].first } until members.maxOf { wordTimes[it].last + 1 }
        }
        val keyword = if (style.wordAnimation == WordAnimation.KEYWORD) {
            WordMotion.keyword(tokens.map { text.substring(it) })
        } else {
            -1
        }
        return tokens.mapIndexed { i, tk ->
            val time = timeOf(i)
            val kw = keyword == i || (byLine && keyword >= 0 && lines[keyword] == lines[i])
            Unit(tk.first, tk.last + 1, lines[i], WordMotion.at(style, time, localMs, kw), localMs in time)
        }
    }

    @Suppress("LongMethod")
    private fun drawWords(canvas: Canvas, plan: Plan, layer: TextLayer, units: List<Unit>, chars: Int) {
        val style = layer.style
        val paint = plan.paint
        val layout = plan.layout
        val fontPx = plan.fontPx
        val text = layout.text
        val hlColor = layer.highlightColor.takeIf { it != 0 } ?: CaptionWords.HIGHLIGHT
        canvas.translate(plan.pad, plan.pad)
        if (style.background > 0) drawBackground(canvas, plan, style)
        val byLine = style.wordUnit == WordUnit.LINE
        for (u in units) {
            if (u.start >= chars) continue
            val f = u.frame
            if (f.alpha <= 0.004f) continue
            val x0 = layout.getPrimaryHorizontal(u.start)
            val x1 = x0 + paint.measureText(text, u.start, u.end)
            val top = layout.getLineTop(u.line).toFloat()
            val bottom = layout.getLineBottom(u.line).toFloat()
            val baseline = layout.getLineBaseline(u.line).toFloat()
            val cx = if (byLine) (layout.getLineLeft(u.line) + layout.getLineRight(u.line)) / 2 else (x0 + x1) / 2
            val cy = (top + bottom) / 2
            canvas.save()
            canvas.translate(0f, f.dy * fontPx)
            canvas.scale(f.scale, f.scale, cx, cy)
            val alpha = f.alpha
            val highlight = when {
                style.wordAnimation == WordAnimation.NONE -> if (u.active && layer.highlightColor != 0) 1f else 0f
                else -> f.highlight
            }
            if (style.wordBox && (u.active || highlight > 0.5f)) {
                val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = withAlpha(hlColor, alpha)
                }
                val r = maxOf(style.backgroundRadius, 0.18f) * fontPx
                canvas.drawRoundRect(
                    RectF(x0 - BG_PAD_X * fontPx * 0.6f, top - BG_PAD_Y * fontPx, x1 + BG_PAD_X * fontPx * 0.6f, bottom + BG_PAD_Y * fontPx),
                    r, r, box
                )
            }
            val fill = if (style.wordBox && (u.active || highlight > 0.5f)) {
                // Text on the word box: the layer color stays readable (box is the highlight).
                layer.color
            } else {
                blend(layer.color, hlColor, highlight)
            }
            paint.shader = null
            if (style.glow > 0) {
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(style.glowColor, alpha)
                paint.setShadowLayer(style.glow * fontPx * 0.5f, 0f, 0f, withAlpha(style.glowColor, alpha))
                repeat(2) { canvas.drawText(text, u.start, u.end, x0, baseline, paint) }
            }
            if (style.shadow > 0) {
                val distance = style.shadowDistance * fontPx * 0.7071f
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(fill, alpha)
                paint.setShadowLayer(
                    max(0.5f, style.shadowBlur * fontPx),
                    distance,
                    distance,
                    withAlpha(style.shadowColor, style.shadow * alpha)
                )
                canvas.drawText(text, u.start, u.end, x0, baseline, paint)
            }
            paint.clearShadowLayer()
            if (style.strokeWidth > 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = 2 * style.strokeWidth * fontPx
                paint.color = withAlpha(style.strokeColor, alpha)
                canvas.drawText(text, u.start, u.end, x0, baseline, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(fill, alpha)
            if (style.gradientColor != 0 && highlight < 0.5f) {
                paint.shader = LinearGradient(
                    0f, top, 0f, bottom,
                    withAlpha(fill, alpha), withAlpha(style.gradientColor, alpha), Shader.TileMode.CLAMP
                )
            }
            canvas.drawText(text, u.start, u.end, x0, baseline, paint)
            paint.shader = null
            canvas.restore()
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        if (t <= 0f) return a
        if (t >= 1f) return b
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt() shl shift
        return ch(24) or ch(16) or ch(8) or ch(0)
    }

    private fun drawBackground(canvas: Canvas, plan: Plan, style: TextStyle) {
        val layout = plan.layout
        val padX = BG_PAD_X * plan.fontPx
        val padY = BG_PAD_Y * plan.fontPx
        val radius = style.backgroundRadius * plan.fontPx
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(style.backgroundColor, style.background)
        }
        for (line in 0 until layout.lineCount) {
            val left = layout.getLineLeft(line)
            val right = layout.getLineRight(line)
            if (right - left < 1) continue
            val rect = RectF(
                left - padX,
                layout.getLineTop(line) - padY,
                right + padX,
                layout.getLineBottom(line) + padY
            )
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((Color.alpha(color) * alpha).toInt().coerceIn(0, 255) shl 24)
}
