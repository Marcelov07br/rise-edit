package br.com.riseedit.core.timeline

import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project

/** Read-only projection. No storage migration or player integration is performed. */
fun Project.toComposition(): Composition {
    val mainId = "video:main"
    val tracks = mutableListOf(
        Track(
            mainId,
            "Main",
            TrackKind.VIDEO,
            0,
            clips.mapIndexed { index, clip -> clip.segment("main:$index:${clip.id}") }
        )
    )
    overlays.forEachIndexed { index, overlay ->
        tracks += Track(
            "video:overlay:${overlay.track}:$index:${overlay.id}",
            "Overlay ${overlay.track + 1}.${index + 1}",
            TrackKind.VIDEO,
            micros(overlay.offsetMs),
            listOf(overlay.clip.segment("overlay:$index:${overlay.clip.id}"))
        )
    }
    // One physical track per audio item preserves overlaps within the same legacy track index.
    audio.forEachIndexed { index, item ->
        tracks += Track(
            "audio:${item.track}:$index:${item.id}",
            "Audio ${item.track + 1}.${index + 1}",
            TrackKind.AUDIO,
            micros(item.offsetMs),
            listOf(
                Segment(
                    "audio-segment:$index:${item.id}",
                    SegmentSource.Media(item.uri, micros(item.sourceDurationMs)),
                    ConstantSpeedMap(TimeRange.fromMs(item.startMs, item.endMs), 1.0)
                )
            )
        )
    }
    return Composition(tracks, mainId).also { it.validate() }
}

private fun Clip.segment(segmentId: String): Segment {
    val boundaries = (
        listOf(startMs) + speedPoints.map { it.sourceMs }
            .filter { it > startMs && it < endMs } + endMs
        ).distinct().sorted()
    val pieces = boundaries.zipWithNext().map { (start, end) ->
        ConstantSpeedMap(TimeRange.fromMs(start, end), speedAt(start))
    }
    val map = if (pieces.size ==
        1
    ) {
        pieces.single()
    } else {
        PiecewiseMap(pieces, cumulativeRounding = true)
    }
    return Segment(segmentId, SegmentSource.Media(uri, micros(sourceDurationMs)), map)
}

private fun micros(milliseconds: Long): Long = Math.multiplyExact(milliseconds, 1000)
