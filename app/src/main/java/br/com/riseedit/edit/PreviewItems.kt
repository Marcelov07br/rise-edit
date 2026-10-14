package br.com.riseedit.edit

import br.com.riseedit.EditorState
import br.com.riseedit.core.Clip
import br.com.riseedit.core.TextLayer
import br.com.riseedit.core.Timeline
import br.com.riseedit.core.TransformEdits
import br.com.riseedit.core.TransformKeyframe
import br.com.riseedit.core.VideoOverlay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Something that can be manipulated directly on the preview. Sizes are fractions of the frame. */
internal sealed interface PreviewItem {
    val id: String
    val transform: TransformKeyframe

    /** Item time used for keyframes (clip source ms, or layer-local ms). */
    val keyTimeMs: Long

    /** Unscaled size as a fraction of the output frame (width, height). */
    val baseSize: Pair<Float, Float>
    val keyframes: List<TransformKeyframe>

    data class Main(val index: Int, val clip: Clip, override val keyTimeMs: Long) : PreviewItem {
        override val id get() = clip.id
        override val transform get() = Timeline.keyframe(clip, keyTimeMs)
        override val baseSize get() = 1f to 1f
        override val keyframes get() = clip.keyframes
    }

    data class Overlay(
        val layer: VideoOverlay,
        override val keyTimeMs: Long,
        val aspect: Float,
        val frameAspect: Float
    ) : PreviewItem {
        override val id get() = layer.id
        override val transform get() = layer.transformAt(keyTimeMs)
        override val baseSize get() = 1f to frameAspect / aspect
        override val keyframes get() = layer.keyframes
    }

    data class Text(val layer: TextLayer, override val keyTimeMs: Long, val frameAspect: Float) :
        PreviewItem {
        override val id get() = layer.id
        override val transform get() = layer.transformAt(keyTimeMs)
        override val keyframes get() = layer.keyframes
        override val baseSize: Pair<Float, Float> get() {
            val lines = wrappedLines(layer.text)
            val height = TEXT_LINE_FRACTION * lines.size
            val width =
                TEXT_LINE_FRACTION * CHAR_ASPECT * (lines.maxOfOrNull { it.length } ?: 1) /
                    frameAspect
            return width.coerceAtMost(0.9f) to height
        }
    }

    companion object {
        /** TimedText renders 42 px lines at a 720 px frame height. */
        const val TEXT_LINE_FRACTION = 42f / 720f * 1.2f
        const val CHAR_ASPECT = 0.58f

        fun wrappedLines(text: String) = text.split('\n').flatMap {
            it.chunked(24).ifEmpty { listOf("") }
        }
    }
}

/** Items visible at the playhead, bottom to top (main video, overlays, texts). */
internal fun previewItems(
    state: EditorState,
    mainIndex: Int?,
    mainSourceMs: Long,
    frameAspect: Float
): List<PreviewItem> = buildList {
    val project = state.project
    val position = state.positionMs
    mainIndex?.let { i ->
        project.clips.getOrNull(i)?.let { add(PreviewItem.Main(i, it, mainSourceMs)) }
    }
    project.overlays.filter { position in it.offsetMs until it.offsetMs + it.clip.durationMs }
        .forEach {
            add(PreviewItem.Overlay(it, position - it.offsetMs, aspectOf(it.clip), frameAspect))
        }
    project.texts.filter { position in it.startMs until it.endMs }
        .forEach { add(PreviewItem.Text(it, position - it.startMs, frameAspect)) }
}

private fun aspectOf(clip: Clip): Float {
    val info = clip.sourceInfo ?: return 16f / 9f
    if (info.width <= 0 || info.height <= 0) return 16f / 9f
    val rotated = info.rotationDegrees % 180 != 0
    return if (rotated) info.height.toFloat() / info.width else info.width.toFloat() / info.height
}

/** Half extents in NDC (frame spans -1..1) of the item's rotated bounding box. */
internal fun halfExtents(item: PreviewItem, t: TransformKeyframe): Pair<Double, Double> {
    val (w, h) = item.baseSize
    val sw = w * t.scale
    val sh = h * t.scale
    val r = Math.toRadians(t.rotation.toDouble())
    val c = abs(cos(r))
    val s = abs(sin(r))
    return (sw * c + sh * s).toDouble() to (sw * s + sh * c).toDouble()
}

/** Hit test in NDC with the item's rotation taken into account. */
internal fun PreviewItem.contains(x: Double, y: Double, frameAspect: Float): Boolean {
    val t = transform
    val (w, h) = baseSize
    val dx = (x - t.x) * frameAspect
    val dy = y - t.y
    val r = Math.toRadians(-t.rotation.toDouble())
    val lx = (dx * cos(r) - dy * sin(r)) / frameAspect
    val ly = dx * sin(r) + dy * cos(r)
    val margin = 0.04
    return abs(lx) <= max(w * t.scale.toDouble(), 0.1) + margin &&
        abs(ly) <= max(h * t.scale.toDouble(), 0.1) + margin
}

/** Writes a manipulation result back through the item's own edit rule. */
internal fun PreviewItem.apply(t: TransformKeyframe): PreviewEdit = when (this) {
    is PreviewItem.Main -> PreviewEdit.ClipEdit(index, TransformEdits.clip(clip, keyTimeMs, t))
    is PreviewItem.Overlay -> PreviewEdit.OverlayEdit(
        TransformEdits.overlay(layer, keyTimeMs, t)
    )
    is PreviewItem.Text -> PreviewEdit.TextEdit(TransformEdits.text(layer, keyTimeMs, t))
}

internal fun PreviewItem.reset(): PreviewEdit = when (this) {
    is PreviewItem.Main -> PreviewEdit.ClipEdit(index, TransformEdits.resetClip(clip))
    is PreviewItem.Overlay -> PreviewEdit.OverlayEdit(TransformEdits.resetOverlay(layer))
    is PreviewItem.Text -> PreviewEdit.TextEdit(TransformEdits.resetText(layer))
}

internal fun PreviewItem.toggleKey(): PreviewEdit = when (this) {
    is PreviewItem.Main -> PreviewEdit.ClipEdit(
        index,
        clip.copy(keyframes = TransformEdits.toggle(clip.keyframes, keyTimeMs, transform))
    )
    is PreviewItem.Overlay -> PreviewEdit.OverlayEdit(
        layer.copy(keyframes = TransformEdits.toggle(layer.keyframes, keyTimeMs, transform))
    )
    is PreviewItem.Text -> PreviewEdit.TextEdit(
        layer.copy(keyframes = TransformEdits.toggle(layer.keyframes, keyTimeMs, transform))
    )
}

internal sealed interface PreviewEdit {
    data class ClipEdit(val index: Int, val clip: Clip) : PreviewEdit
    data class OverlayEdit(val layer: VideoOverlay) : PreviewEdit
    data class TextEdit(val layer: TextLayer) : PreviewEdit
}
