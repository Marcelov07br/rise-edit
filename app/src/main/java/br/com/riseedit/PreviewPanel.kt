@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package br.com.riseedit

import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnPreDraw
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import br.com.riseedit.core.*
import br.com.riseedit.core.TextLayer
import br.com.riseedit.edit.FrameSampler
import br.com.riseedit.edit.PreviewEditLayer
import br.com.riseedit.edit.PreviewEditState
import br.com.riseedit.edit.previewItems
import br.com.riseedit.media.displayMessage
import br.com.riseedit.preview.PreviewQuality
import kotlin.math.*
import kotlinx.coroutines.launch

/**
 * Transform applied on top of the decoded frame. Rápida draws the whole clip transform here. Fiel
 * already renders the committed transform in its GL graph, so only the delta of an in-progress
 * gesture draft is applied: live feedback without recreating the CompositionPlayer.
 */
internal fun mainTransform(
    fast: Boolean,
    clip: Clip?,
    draft: TransformKeyframe?,
    sourceMs: Long
): TransformKeyframe {
    if (clip == null) return TransformKeyframe(0)
    if (fast) return draft ?: Timeline.keyframe(clip, sourceMs)
    if (draft == null) return TransformKeyframe(0)
    val committed = Timeline.keyframe(clip, sourceMs)
    return TransformKeyframe(
        0,
        draft.x - committed.x,
        draft.y - committed.y,
        if (committed.scale >
            0f
        ) {
            draft.scale / committed.scale
        } else {
            1f
        },
        draft.rotation - committed.rotation,
        if (committed.opacity > 0f) (draft.opacity / committed.opacity).coerceIn(0f, 1f) else 1f
    )
}

/** TextureView lets the cheap preview transform decoded frames without the GL effect graph. */
@Composable
fun PreviewPanel(
    vm: EditorViewModel,
    state: EditorState,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    mainSelected: Boolean = false,
    onSelect: (Boolean) -> Unit = {},
    textDraft: TextLayer? = null,
    onDraftTransform: (TextLayer) -> Unit = {},
    pickColor: ((Int) -> Unit)? = null
) {
    val previewPlayer = vm.player
    val fast = previewPlayer is ExoPlayer
    val clip = vm.previewClip
    val analysis = clip?.let { vm.motionAnalysis(it.uri) }
    val corrections =
        remember(analysis, clip?.stabilization, clip?.startMs, clip?.endMs, state.comparing) {
            if (analysis != null &&
                clip != null &&
                clip.stabilization.enabled &&
                !state.comparing
            ) {
                MotionSmoother.corrections(analysis, clip.stabilization, clip.startMs, clip.endMs)
            } else {
                emptyList()
            }
        }
    // Stabilization now runs in the fast preview's GL pipeline (same shader as the export),
    // reading these live values each frame — no player rebuild while dragging sliders.
    StabilizationLive.corrections = corrections
    StabilizationLive.analysis = if (corrections.isEmpty()) null else analysis
    StabilizationLive.settings = clip?.stabilization ?: Stabilization()
    StabilizationLive.comparing = state.comparing
    StabilizationLive.sourceMs = vm.previewSourceMs
    // The paused overlay re-draws frames without the GL pipeline: it can reproduce the rigid
    // pose/zoom with a graphicsLayer, but not the per-line warp. RIGID keeps the cheap overlay;
    // WARP falls back to a same-position seek so the paused frame matches play exactly.
    val overlayFriendly = corrections.isEmpty() ||
        clip?.stabilization?.method != StabilizeMethod.WARP
    val zoom = corrections.takeIf { it.isNotEmpty() }
        ?.let { MotionSmoother.zoomAt(it, vm.previewSourceMs).toFloat() } ?: 1f
    val pose = if (overlayFriendly && corrections.isNotEmpty()) {
        MotionSmoother.interpolate(corrections, vm.previewSourceMs)
    } else {
        Pose()
    }
    LaunchedEffect(corrections, overlayFriendly, state.playing, fast) {
        if (!fast) return@LaunchedEffect
        StabilizationLive.tick.intValue++
        if (!state.playing) redrawColor(previewPlayer)
    }
    val edit = remember { PreviewEditState() }
    val key = mainTransform(fast, clip, edit.draftFor(clip?.id), vm.previewSourceMs)
    val density = LocalDensity.current.density
    val info = clip?.sourceInfo
    val rotated = info?.rotationDegrees in listOf(90, 270)
    val sourceRatio = if (info != null && info.width > 0 && info.height > 0) {
        if (rotated) info.height.toFloat() / info.width else info.width.toFloat() / info.height
    } else if (analysis != null) {
        analysis.width.toFloat() / analysis.height
    } else {
        16f / 9
    }
    val outputRatio = state.project.aspectRatio.takeIf { it > 0 } ?: sourceRatio
    // Color keyframes: evaluated at the current source time (paused overlay uses this value;
    // while playing the live effect reads ColorLive.sourceMs, updated here every frame).
    val animatedColor = clip?.colorKeys?.isEmpty == false || clip?.masks?.isAnimated == true
    br.com.riseedit.color.ColorLive.sourceMs = vm.previewSourceMs
    // Camadas de ajuste: live effect reads these (project time); paused overlay gets the passes.
    br.com.riseedit.color.ColorLive.layers = state.project.adjustmentLayers
    br.com.riseedit.color.ColorLive.projectMs = state.positionMs
    val layerPasses = androidx.compose.runtime.remember(
        state.project.adjustmentLayers,
        state.positionMs
    ) {
        br.com.riseedit.core.AdjustmentStack.at(state.project.adjustmentLayers, state.positionMs)
    }
    val (adjust, grade) = clip?.colorAt(vm.previewSourceMs)
        ?: (ColorAdjust.NEUTRAL to br.com.riseedit.core.color.ColorGrade())
    androidx.compose.runtime.LaunchedEffect(adjust, grade, fast, layerPasses) {
        if (!fast) return@LaunchedEffect
        br.com.riseedit.color.ColorLive.adjust = adjust
        br.com.riseedit.color.ColorLive.grade = grade
        br.com.riseedit.color.ColorLive.keys = clip?.takeIf { animatedColor }
        if (state.playing && animatedColor) return@LaunchedEffect // live effect samples per frame
        if (animatedColor) {
            android.util.Log.d(
                "RiseColor",
                "keyframed color at ${vm.previewSourceMs}ms groups=${clip?.colorKeys?.tracks?.keys}"
            )
        }
        redrawColor(previewPlayer)
    }
    val index = state.project.clips.indexOfFirst { it.id == clip?.id }
    val local = (
        state.positionMs -
            state.project.clips.take(index.coerceAtLeast(0)).sumOf { it.durationMs }
        ).coerceAtLeast(0)
    val fade = clip?.let { min(it.fadeMs, it.durationMs / 2) } ?: 0
    val gain = if (!fast ||
        fade <= 0 ||
        clip == null
    ) {
        1f
    } else {
        min(
            1f,
            min(local.toFloat() / fade, (clip.durationMs - local).toFloat() / fade)
        ).coerceIn(0f, 1f)
    }

    BoxWithConstraints(
        modifier.background(Color.Black).clip(RoundedCornerShape(16.dp)).then(
            if (editable) {
                Modifier
            } else {
                Modifier.clickable {
                    vm.togglePlay()
                }
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        val outWidth = min(maxWidth.value, maxHeight.value * outputRatio)
        val outHeight = outWidth / outputRatio
        val fill = clip?.orientation?.scaleMode != br.com.riseedit.core.ScaleMode.FIT
        val videoWidth: Float
        val videoHeight: Float
        if (!fast) {
            videoWidth = outWidth
            videoHeight = outHeight
        } else if (fill) {
            videoWidth = max(outWidth, outHeight * sourceRatio)
            videoHeight = videoWidth / sourceRatio
        } else {
            videoWidth = min(outWidth, outHeight * sourceRatio)
            videoHeight = videoWidth / sourceRatio
        }
        if (state.project.clips.isEmpty()) {
            Text("PRÉVIA", color = Color.Gray)
        } else if (state.previewFault !=
            null
        ) {
            val tech = (state.previewFault as? br.com.riseedit.core.MediaFault.PreviewFailed)?.technical
            var open by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(false)
            }
            androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                Text(
                    state.previewFault.displayMessage(),
                    style = MaterialTheme.typography.bodySmall
                )
                if (tech != null) {
                    Text(
                        if (open) "Ocultar detalhes ▲" else "Detalhes técnicos ▼",
                        Modifier.clickable { open = !open }.padding(top = 8.dp),
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (open) {
                        Text(
                            tech,
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        } else {
            Box(
                Modifier.size(outWidth.dp, outHeight.dp).clip(RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.requiredSize(videoWidth.dp, videoHeight.dp).graphicsLayer {
                        val o = clip?.orientation
                        val flipX = if (o?.flipHorizontal == true) -1f else 1f
                        val flipY = if (o?.flipVertical == true) -1f else 1f
                        scaleX = key.scale * flipX
                        scaleY = key.scale * flipY
                        rotationZ = -(key.rotation + (o?.rotationDegrees ?: 0f))
                        translationX = key.x * videoWidth * density / 2
                        translationY = -key.y * videoHeight * density / 2
                        alpha = key.opacity * gain
                    }
                ) {
                    key(previewPlayer) {
                        AndroidView(
                            factory = { context ->
                                (
                                    LayoutInflater.from(
                                        context
                                    ).inflate(
                                        if (fast) R.layout.preview_player else R.layout.preview_player_faithful,
                                        null
                                    ) as PlayerView
                                    ).apply {
                                    setEnableComposeSurfaceSyncWorkaround(true)
                                    player = previewPlayer
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                    if (fast) {
                                        doOnPreDraw {
                                            post {
                                                // A seek to the same position is ignored by ExoPlayer. Reprepare after the real surface exists.
                                                if (isAttachedToWindow &&
                                                    player === previewPlayer &&
                                                    previewPlayer.mediaItemCount > 0 &&
                                                    !previewPlayer.playWhenReady
                                                ) {
                                                    val index = previewPlayer.currentMediaItemIndex
                                                    val position = previewPlayer.currentPosition
                                                    previewPlayer.stop()
                                                    previewPlayer.seekTo(index, position)
                                                    previewPlayer.prepare()
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            update = { view ->
                                if (view.player !== previewPlayer) view.player = previewPlayer
                            },
                            // Stabilization runs inside the player's GL pipeline (live effect):
                            // no Compose-side pose/zoom approximation anymore.
                            modifier = Modifier.fillMaxSize(),
                            onRelease = { it.player = null }
                        )
                    }
                    if (fast && clip != null && overlayFriendly) {
                        br.com.riseedit.color.PausedColorOverlay(
                            clip = clip,
                            sourceMs = { vm.previewSourceMs },
                            playing = state.playing,
                            adjust = adjust,
                            grade = grade,
                            layers = layerPasses,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                rotationZ = (pose.angle * 180 / PI).toFloat()
                                translationX =
                                    if (analysis ==
                                        null
                                    ) {
                                        0f
                                    } else {
                                        (
                                            pose.x * videoWidth * density /
                                                analysis.width
                                            ).toFloat()
                                    }
                                translationY =
                                    if (analysis ==
                                        null
                                    ) {
                                        0f
                                    } else {
                                        (
                                            pose.y * videoHeight * density /
                                                analysis.height
                                            ).toFloat()
                                    }
                            }
                        )
                    }
                }
                if (editable) {
                    val items =
                        previewItems(
                            state,
                            index.takeIf {
                                it >= 0
                            },
                            vm.previewSourceMs,
                            outputRatio,
                            textDraft
                        )
                    val selectedId =
                        textDraft?.id ?: state.layerId ?: clip?.id?.takeIf { mainSelected }
                    val context = LocalContext.current
                    val scope = rememberCoroutineScope()
                    val pick = pickColor?.let { done ->
                        { u: Float, v: Float ->
                            clip?.let { c ->
                                scope.launch {
                                    FrameSampler.sample(
                                        context,
                                        c,
                                        vm.previewSourceMs,
                                        u,
                                        v
                                    )?.let(done)
                                }
                            }
                            Unit
                        }
                    }
                    PreviewEditLayer(vm, state, edit, items, selectedId, fast, onSelect, textDraft?.id, onDraftTransform, pick)
                    // Vinheta tab open: draggable center on top of the edit layer, in video-frame coordinates.
                    if (br.com.riseedit.tools.ColorTools.vignetteCenter != null) {
                        Box(Modifier.requiredSize(videoWidth.dp, videoHeight.dp)) {
                            br.com.riseedit.tools.VignetteHandle()
                        }
                    }
                    // Ferramenta Máscara open: outlines + handles in the target's frame.
                    MaskHandleLayer(
                        state,
                        outWidth,
                        outHeight,
                        videoWidth,
                        videoHeight,
                        outputRatio
                    )
                }
            }
        }
        Surface(
            Modifier.align(Alignment.TopStart).padding(6.dp),
            color = Color.Black.copy(alpha = 0.7f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    vm.previewQuality(PreviewQuality.Fast)
                }) { Text(if (fast) "• Rápida" else "Rápida") }
                TextButton(onClick = {
                    vm.previewQuality(PreviewQuality.Faithful)
                }) { Text(if (fast) "Fiel" else "• Fiel") }
            }
        }
        if (clip != null) {
            br.com.riseedit.color.ScopesOverlay(
                clip,
                { vm.previewSourceMs },
                state.playing,
                Modifier.align(Alignment.TopEnd).padding(6.dp)
            )
        }
        if (state.comparing) {
            Text(
                "Original · sem estabilização",
                Modifier.align(Alignment.CenterStart).padding(8.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
        val notice =
            state.previewDegraded?.let { "Fiel indisponível · $it Camadas e áudio extra omitidos." }
                ?: if (fast) "Rápida (scrub) · camadas e áudio extra só na Fiel" else null
        if (notice !=
            null
        ) {
            Text(
                notice,
                Modifier.align(
                    Alignment.BottomCenter
                ).background(Color.Black.copy(alpha = 0.7f)).padding(6.dp).semantics {
                    contentDescription =
                        "Aviso da prévia"
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Repaints the paused frame after a live color change (fast preview only). */
internal fun redrawColor(player: androidx.media3.common.Player?) {
    br.com.riseedit.color.ColorLive.tick.intValue++
    val exo = player as? ExoPlayer ?: return
    if (br.com.riseedit.color.ColorLive.overlayActive) {
        android.util.Log.v("RiseColor", "redraw via overlay (no seek)")
        return
    }
    android.util.Log.d("RiseColor", "redraw via seek (overlay inactive) pos=${exo.currentPosition}")
    // VideoFrameProcessor.REDRAW needs a replayable frame cache that ExoPlayer never enables:
    // redraw() throws UnsupportedOperationException on the playback thread -> player error.
    // An exact seek to the same position re-decodes the frame through the live effect instead.
    if (!exo.isPlaying &&
        exo.mediaItemCount > 0 &&
        exo.playbackState != androidx.media3.common.Player.STATE_IDLE
    ) {
        runCatching { exo.seekTo(exo.currentMediaItemIndex, exo.currentPosition) }
    }
}

/**
 * Mask handles over the preview while the Máscara tool is open: clip = video frame box (same
 * coords as the take shader), layer = output frame, overlay = the overlay's own box (rotated).
 */
@Composable
private fun MaskHandleLayer(
    state: EditorState,
    outWidth: Float,
    outHeight: Float,
    videoWidth: Float,
    videoHeight: Float,
    outputRatio: Float
) {
    val live = br.com.riseedit.tools.MaskLive
    val owner = live.owner ?: return
    if (live.shown.none { it.isGeometric }) return
    val handles = @Composable { m: Modifier ->
        br.com.riseedit.tools.MaskHandles(
            live.shown,
            live.selectedId,
            { live.selectedId = it },
            { id, g -> live.onGeom?.invoke(id, g) },
            m
        )
    }
    when (owner) {
        br.com.riseedit.tools.MaskOwner.CLIP -> Box(
            Modifier.requiredSize(videoWidth.dp, videoHeight.dp)
        ) {
            handles(Modifier)
        }
        br.com.riseedit.tools.MaskOwner.LAYER -> Box(
            Modifier.requiredSize(outWidth.dp, outHeight.dp)
        ) {
            handles(Modifier)
        }
        br.com.riseedit.tools.MaskOwner.OVERLAY -> {
            val o = state.project.overlays.firstOrNull { it.id == live.ownerId } ?: return
            val t = o.transformAt((state.positionMs - o.offsetMs).coerceAtLeast(0))
            val (fw, fh) = 1f to outputRatio / br.com.riseedit.edit.overlayAspect(o.clip)
            val w = fw * t.scale * outWidth
            val h = fh * t.scale * outHeight
            val cx = (t.x + 1) / 2 * outWidth
            val cy = (1 - t.y) / 2 * outHeight
            Box(Modifier.requiredSize(outWidth.dp, outHeight.dp)) {
                Box(
                    Modifier.offset(
                        (cx - w / 2).dp,
                        (cy - h / 2).dp
                    ).size(w.coerceAtLeast(1f).dp, h.coerceAtLeast(1f).dp)
                        .graphicsLayer { rotationZ = -t.rotation }
                ) { handles(Modifier) }
            }
        }
    }
}
