package br.com.riseedit.export

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.RiseAccent
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseOutline
import br.com.riseedit.RiseSurface
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseWhite
import br.com.riseedit.designsystem.ActionBar
import br.com.riseedit.designsystem.PanelAction
import br.com.riseedit.editor.ExportBus
import br.com.riseedit.editor.ExportService
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 0.21: app-wide render UI, drawn above every route (CapCut-style, inside the app — not a system
 * overlay): the render tab, or the floating bubble when minimized.
 */
@Composable
fun RenderHost() {
    val s by ExportBus.state.collectAsState()
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.active) { while (s.active) { now = System.currentTimeMillis(); delay(1000) } }
    fun openResult() {
        val uri = s.result?.let(Uri::parse) ?: return
        val type = context.contentResolver.getType(uri) ?: "video/*"
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        }
        ExportBus.dismiss()
    }
    val visible = s.active || s.done
    if (visible && s.panel) {
        RenderTab(s, now, onMinimize = ExportBus::minimize, onCancel = { ExportService.cancel(context) },
            onOpen = ::openResult, onClose = ExportBus::dismiss)
    }
    if (visible && s.minimized) RenderBubble(s, onTap = { if (s.done) openResult() else ExportBus.open() })
}

fun remainingLabel(ms: Long?): String = when {
    ms == null -> "Calculando tempo restante…"
    ms < 60_000 -> "≈ ${(ms / 1000).coerceAtLeast(1)} s restantes"
    else -> "≈ ${(ms + 30_000) / 60_000} min restantes"
}

/** Render tab: progress, phase, time left, minimize/cancel (or open when done). */
@Composable
fun RenderTab(
    s: ExportBus.State,
    now: Long,
    onMinimize: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onMinimize),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.padding(12.dp).widthIn(max = 520.dp).fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)).background(RiseSurface)
                .border(1.dp, RiseOutline, RoundedCornerShape(24.dp))
                .clickable(enabled = false) {}.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (s.done) "Exportação concluída" else "Renderizando", color = RiseWhite,
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (s.done) "Toque em reproduzir para abrir o vídeo." else s.phase, color = RiseMuted, fontSize = 12.sp)
                }
                ProgressRing(s.progress, s.done, 64.dp)
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(RiseSurfaceHigh)) {
                Box(Modifier.fillMaxWidth(s.progress.coerceIn(0f, 1f)).height(6.dp).clip(CircleShape).background(RiseAccent))
            }
            Row {
                Text("${(s.progress * 100).toInt()}%", color = RiseWhite, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(if (s.done) "Pronto" else remainingLabel(s.remainingMs(now)), color = RiseMuted, fontSize = 13.sp)
            }
            Box(Modifier.fillMaxWidth()) {
                ActionBar(
                    if (s.done) listOf(
                        PanelAction(Icons.Rounded.Close, "Fechar", onClick = onClose),
                        PanelAction(Icons.Rounded.PlayArrow, "Abrir vídeo", primary = true, onClick = onOpen)
                    ) else listOf(
                        PanelAction(Icons.Rounded.CloseFullscreen, "Minimizar", onClick = onMinimize),
                        PanelAction(Icons.Rounded.Close, "Cancelar exportação", danger = true, onClick = onCancel)
                    ),
                    Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
fun ProgressRing(progress: Float, done: Boolean, size: androidx.compose.ui.unit.Dp) {
    val accent = RiseAccent
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val w = 4.dp.toPx()
            drawArc(RiseSurfaceHigh, 0f, 360f, false, style = Stroke(w))
            drawArc(accent, -90f, 360f * progress.coerceIn(0f, 1f), false, style = Stroke(w, cap = StrokeCap.Round))
        }
        if (done) Icon(Icons.Rounded.Check, "Concluído", tint = accent, modifier = Modifier.size(size * 0.45f))
        else Text("${(progress * 100).toInt()}", color = RiseWhite, fontSize = (size.value * 0.26f).sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Draggable bubble; on release it snaps to the nearest side edge with a low-bounce spring. */
@Composable
fun RenderBubble(s: ExportBus.State, initialFromTop: Float = 0.4f, onTap: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val bubble = with(d) { 60.dp.toPx() }
        val margin = with(d) { 12.dp.toPx() }
        val w = with(d) { maxWidth.toPx() }
        val h = with(d) { maxHeight.toPx() }
        val pos = remember { Animatable(Offset(w - bubble - margin, h * initialFromTop), Offset.VectorConverter) }
        val scope = rememberCoroutineScope()
        Box(
            Modifier.offset { IntOffset(pos.value.x.roundToInt(), pos.value.y.roundToInt()) }.size(60.dp)
                .clip(CircleShape).background(RiseSurface).border(1.dp, RiseOutline, CircleShape)
                .pointerInput(w, h) {
                    detectDragGestures(onDragEnd = {
                        val x = if (pos.value.x + bubble / 2 < w / 2) margin else w - bubble - margin
                        val y = pos.value.y.coerceIn(margin, h - bubble - margin)
                        scope.launch { pos.animateTo(Offset(x, y), spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)) }
                    }) { change, drag ->
                        change.consume()
                        scope.launch { pos.snapTo(pos.value + drag) }
                    }
                }
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center
        ) { ProgressRing(s.progress, s.done, 56.dp) }
    }
}
