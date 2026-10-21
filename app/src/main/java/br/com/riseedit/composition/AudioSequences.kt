@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit.composition

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import br.com.riseedit.AudioEnvelope
import br.com.riseedit.core.Project
import br.com.riseedit.core.audibleAudio
import kotlin.math.min

internal fun buildAudioSequences(project: Project): List<EditedMediaItemSequence> =
    project.audibleAudio().also { list ->
        android.util.Log.i(
            "RiseAudio",
            "composição (fiel/export): ${list.size} de ${project.audio.size} faixa(s) de áudio"
        )
    }.map { audio ->
        val durationMs = min(audio.durationMs, project.durationMs - audio.offsetMs)
        android.util.Log.i(
            "RiseAudio",
            "seq ${audio.name}: gap=${audio.offsetMs} src=${audio.startMs}+$durationMs vol=${audio.volume}"
        )
        val media = MediaItem.Builder()
            .setUri(audio.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(audio.startMs)
                    .setEndPositionMs(audio.startMs + durationMs)
                    .build()
            )
            .build()
        val sequence = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        if (audio.offsetMs > 0) sequence.addGap(audio.offsetMs * 1000)
        sequence.addItem(
            EditedMediaItem.Builder(media)
                .setDurationUs(audio.sourceDurationMs * 1000)
                .setRemoveVideo(true)
                .setEffects(
                    Effects(
                        listOf(AudioEnvelope(audio.volume, durationMs, audio.fadeMs)),
                        emptyList()
                    )
                )
                .build()
        )
        val tailMs = project.durationMs - audio.offsetMs - durationMs
        if (tailMs > 0) sequence.addGap(tailMs * 1000)
        sequence.build()
    }
