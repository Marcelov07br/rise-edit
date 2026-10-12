@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package br.com.riseedit

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import br.com.riseedit.core.*
import br.com.riseedit.preview.PreviewQuality
import br.com.riseedit.media.displayMessage
import kotlin.math.*

/** TextureView lets the cheap preview transform decoded frames without the GL effect graph. */
@Composable
fun PreviewPanel(vm: EditorViewModel, state: EditorState, modifier: Modifier = Modifier) {
    val fast = state.previewQuality == PreviewQuality.Fast
    val clip = vm.previewClip
    val analysis = clip?.let { vm.motionAnalysis(it.uri) }
    val corrections = remember(analysis, clip?.stabilization, clip?.startMs, clip?.endMs, state.comparing) {
        if (analysis != null && clip != null && clip.stabilization.enabled && !state.comparing)
            MotionSmoother.corrections(analysis, clip.stabilization, clip.startMs, clip.endMs) else emptyList()
    }
    val zoom = remember(corrections, analysis) { if (analysis == null || corrections.isEmpty()) 1f else
        corrections.maxOf { MotionSmoother.requiredZoom(it.pose, analysis.width, analysis.height) }.toFloat() }
    val pose = if (fast && corrections.isNotEmpty()) MotionSmoother.interpolate(corrections, vm.previewSourceMs) else Pose()
    val key = if (fast && clip != null) Timeline.keyframe(clip, vm.previewSourceMs) else TransformKeyframe(0)
    val density = LocalDensity.current.density
    val info = clip?.sourceInfo
    val rotated = info?.rotationDegrees in listOf(90,270)
    val sourceRatio = if (info != null && info.width > 0 && info.height > 0)
        if (rotated) info.height.toFloat()/info.width else info.width.toFloat()/info.height
        else if (analysis != null) analysis.width.toFloat()/analysis.height else 16f/9
    val outputRatio = state.project.aspectRatio.takeIf { it > 0 } ?: sourceRatio
    val filter = remember(clip?.adjustments, fast) { if (fast) FastPreviewStyle.filter(clip?.adjustments ?: Adjustments()) else null }
    val index = state.project.clips.indexOfFirst { it.id == clip?.id }
    val local = (state.positionMs-state.project.clips.take(index.coerceAtLeast(0)).sumOf { it.durationMs }).coerceAtLeast(0)
    val fade = clip?.let { min(it.fadeMs,it.durationMs/2) } ?: 0
    val gain = if (!fast || fade <= 0 || clip == null) 1f else min(1f,min(local.toFloat()/fade,(clip.durationMs-local).toFloat()/fade)).coerceIn(0f,1f)

    BoxWithConstraints(modifier.background(Color.Black).clip(RoundedCornerShape(16.dp)).clickable { vm.togglePlay() }, contentAlignment = Alignment.Center) {
        val outWidth = min(maxWidth.value,maxHeight.value*outputRatio)
        val outHeight = outWidth/outputRatio
        val videoWidth = if (!fast) outWidth else max(outWidth,outHeight*sourceRatio)
        val videoHeight = if (!fast) outHeight else videoWidth/sourceRatio
        if (state.project.clips.isEmpty()) Text("PRÉVIA", color = Color.Gray)
        else if (state.previewFault != null) Text(state.previewFault.displayMessage(), Modifier.padding(16.dp), style=MaterialTheme.typography.bodySmall)
        else Box(Modifier.size(outWidth.dp,outHeight.dp).clip(RectangleShape), contentAlignment=Alignment.Center) {
            Box(Modifier.requiredSize(videoWidth.dp,videoHeight.dp).graphicsLayer {
                scaleX=key.scale;scaleY=key.scale;rotationZ=-key.rotation
                translationX=key.x*videoWidth*density/2;translationY=-key.y*videoHeight*density/2
                alpha=key.opacity*gain
            }) {
                key(fast) { AndroidView(
                    factory={ context -> (LayoutInflater.from(context).inflate(if (fast) R.layout.preview_player else R.layout.preview_player_faithful,null) as PlayerView).apply {
                        player=vm.player;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    } },
                    update={ view ->
                        if (view.player !== vm.player) view.player=vm.player
                        view.setLayerType(if (filter == null) View.LAYER_TYPE_NONE else View.LAYER_TYPE_HARDWARE, filter?.let { Paint().apply { colorFilter=it } })
                    },
                    modifier=Modifier.fillMaxSize().graphicsLayer {
                        scaleX=if(fast) zoom else 1f;scaleY=if(fast) zoom else 1f
                        rotationZ=(pose.angle*180/PI).toFloat()
                        translationX=if(analysis == null) 0f else (pose.x*videoWidth*density/analysis.width).toFloat()
                        translationY=if(analysis == null) 0f else (pose.y*videoHeight*density/analysis.height).toFloat()
                    },
                    onRelease={ it.player=null }
                ) }
            }
        }
        Surface(Modifier.align(Alignment.TopStart).padding(6.dp),color=Color.Black.copy(alpha=0.7f),shape=RoundedCornerShape(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={vm.previewQuality(PreviewQuality.Fast)}) {Text(if(fast) "• Rápida" else "Rápida")}
                TextButton(onClick={vm.previewQuality(PreviewQuality.Faithful)}) {Text(if(fast) "Fiel" else "• Fiel")}
            }
        }
        if (state.comparing) Text("Original · sem estabilização",Modifier.align(Alignment.CenterStart).padding(8.dp),style=MaterialTheme.typography.labelSmall)
        if (fast) Text("Prévia aproximada · camadas e áudio extra na Fiel",Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha=0.7f)).padding(6.dp),style=MaterialTheme.typography.labelSmall)
    }
}
