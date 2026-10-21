package br.com.riseedit.color

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.riseedit.core.Clip
import br.com.riseedit.core.color.Meters
import br.com.riseedit.designsystem.ChannelChips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

enum class ScopeKind(val label: String) { WAVEFORM("Luma"), PARADE("RGB"), VECTOR("Vetor"), HISTOGRAM("Hist") }

/** Scope overlay state (app-wide, survives panel changes). */
object Scopes {
    var visible by mutableStateOf(false)
    var kind by mutableStateOf(ScopeKind.WAVEFORM)
    var widthDp by mutableFloatStateOf(200f)
    const val MIN_W = 128f
    const val MAX_W = 300f
    const val SAMPLE_W = 192
    const val PLAY_INTERVAL_MS = 100L // ~10 fps while playing
}

/**
 * Translucent scope in a corner of the preview. Source = already-colored frame: paused → the
 * PausedColorRenderer output (export shader); playing or no overlay → small decoded frame colored
 * by the CPU mirror at [Clip.colorAt], async ~10 fps, latest wins, never on the UI thread.
 */
@Composable
fun ScopesOverlay(clip: Clip, sourceMs: () -> Long, playing: Boolean, modifier: Modifier = Modifier) {
    if (!Scopes.visible) return
    val context = LocalContext.current
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val currentClip by rememberUpdatedState(clip)
    val currentSource by rememberUpdatedState(sourceMs)
    val kind = Scopes.kind
    LaunchedEffect(playing, kind, clip.effectiveUri(true)) {
        Log.i(TAG, "scopes ${kind.name} ${if (playing) "playing ~10fps" else "paused"}")
        if (playing) {
            var frames = 0
            var since = System.currentTimeMillis()
            while (isActive) {
                val t0 = System.currentTimeMillis()
                val c = currentClip
                val ms = currentSource()
                val out = withContext(Dispatchers.Default) {
                    grab(context, c.effectiveUri(true), ms, Scopes.SAMPLE_W)?.let { colorCpu(it, c, ms) }?.let { renderScope(kind, it) }
                }
                if (out != null) { image = out.asImageBitmap(); frames++ }
                if (t0 - since > 3000) { Log.d(TAG, "scopes rate ${frames * 1000 / (t0 - since)}fps"); frames = 0; since = t0 }
                delay((Scopes.PLAY_INTERVAL_MS - (System.currentTimeMillis() - t0)).coerceAtLeast(10))
            }
        } else {
            snapshotFlow { Triple(ColorLive.scopeFrame.value, currentSource(), currentClip) }.collectLatest { (shaded, ms, c) ->
                delay(60)
                val t0 = System.nanoTime()
                val out = withContext(Dispatchers.Default) {
                    val src = if (shaded != null && ColorLive.overlayActive) shaded.scaledTo(Scopes.SAMPLE_W)
                    else grab(context, c.effectiveUri(true), ms, Scopes.SAMPLE_W)?.let { colorCpu(it, c, ms) }
                    src?.let { renderScope(kind, it) }
                }
                if (out != null) image = out.asImageBitmap()
                Log.d(TAG, "scope ${kind.name} paused src=${if (shaded != null && ColorLive.overlayActive) "shader" else "cpu"} in ${(System.nanoTime() - t0) / 1_000_000}ms")
            }
        }
    }
    ScopesBox(image, kind, modifier)
}

private const val TAG = PausedColorRenderer.TAG

/** Stateless box (also Paparazzi): scope image, scope chips, resize handle. */
@Composable
fun ScopesBox(image: ImageBitmap?, kind: ScopeKind, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.width(Scopes.widthDp.dp).clip(shape).background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape).padding(6.dp)
            .semantics { contentDescription = "Scope ${kind.label}" },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChannelChips(ScopeKind.entries.toList(), kind, { it.label }) { Scopes.kind = it }
        Box(Modifier.fillMaxWidth().aspectRatio(if (kind == ScopeKind.VECTOR) 1f else 16f / 9f)) {
            image?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(if (kind == ScopeKind.VECTOR) 1f else 16f / 9f), contentScale = ContentScale.FillBounds, alpha = 0.92f) }
            Icon(
                Icons.Rounded.OpenInFull, "Redimensionar scope",
                Modifier.align(Alignment.BottomStart).size(18.dp).pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        // Anchored top-end: dragging the bottom-start handle left/down grows it.
                        Scopes.widthDp = (Scopes.widthDp + (-drag.x + drag.y) / density).coerceIn(Scopes.MIN_W, Scopes.MAX_W)
                    }
                },
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

private fun Bitmap.scaledTo(w: Int): Bitmap = if (width <= w) this else Bitmap.createScaledBitmap(this, w, (height * w / width).coerceAtLeast(1), true)

/** CPU mirror (adjust + grade at [ms]) over a small frame. */
private fun colorCpu(frame: Bitmap, clip: Clip, ms: Long): Bitmap {
    val f = frame.scaledTo(Scopes.SAMPLE_W)
    val (a, g) = clip.colorAt(ms)
    val px = IntArray(f.width * f.height)
    f.getPixels(px, 0, f.width, 0, 0, f.width, f.height)
    if (!a.isNeutral || !g.isIdentity) for (i in px.indices) px[i] = GradeLuts.applyFull(a, g, px[i])
    return Bitmap.createBitmap(px, f.width, f.height, Bitmap.Config.ARGB_8888)
}

/** Draws [kind] for [frame] into a bitmap (pure CPU, testable). */
fun renderScope(kind: ScopeKind, frame: Bitmap): Bitmap {
    val px = IntArray(frame.width * frame.height)
    frame.getPixels(px, 0, frame.width, 0, 0, frame.width, frame.height)
    return renderScope(kind, px, frame.width)
}

fun renderScope(kind: ScopeKind, px: IntArray, width: Int): Bitmap {
    val total = px.size.coerceAtLeast(1)
    return when (kind) {
        ScopeKind.WAVEFORM -> traces(listOf(Meters.waveform(px, width, 192, 108)), listOf(0xFFE8F0FF.toInt()), 192, 108, total)
        ScopeKind.PARADE -> {
            val p = Meters.parade(px, width, 64, 108)
            traces(p.map { arrayOf(*it) }, listOf(0xFFFF5A5A.toInt(), 0xFF5AE07A.toInt(), 0xFF5AA0FF.toInt()), 192, 108, total, side = true)
        }
        ScopeKind.VECTOR -> vector(Meters.vectorscope(px, 128), 128, total)
        ScopeKind.HISTOGRAM -> histogram(Meters.histogramRgb(px), 192, 108)
    }
}

private fun traces(chs: List<Array<IntArray>>, colors: List<Int>, w: Int, h: Int, total: Int, side: Boolean = false): Bitmap {
    val out = IntArray(w * h)
    // graticule 0/25/50/75/100%
    for (q in 0..4) { val y = ((h - 1) * (1 - q / 4f)).toInt(); for (x in 0 until w) out[y * w + x] = 0x33FFFFFF }
    val norm = ln(1f + total / 40f)
    chs.forEachIndexed { ci, cols ->
        val c = colors[ci]
        val x0 = if (side) ci * w / chs.size else 0
        val span = if (side) w / chs.size else w
        for (col in cols.indices) for (lv in cols[col].indices) {
            val n = cols[col][lv]; if (n == 0) continue
            val a = (ln(1f + n) / norm * 255f * 2.2f).toInt().coerceIn(40, 255)
            val x = x0 + col * span / cols.size
            val y = h - 1 - lv * h / cols[col].size
            out[y * w + x] = blend(out[y * w + x], c, a)
        }
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

private fun vector(bins: IntArray, n: Int, total: Int): Bitmap {
    val out = IntArray(n * n)
    val c = (n - 1) / 2f
    // circle + skin-tone line (I line) + 75% targets as dots
    for (k in 0 until 360) { val t = Math.toRadians(k.toDouble()); val x = (c + c * cos(t)).toInt(); val y = (c - c * sin(t)).toInt(); out[y * n + x] = 0x44FFFFFF }
    for (r in 0 until n / 2) {
        val x = (c + r * cos(Meters.SKIN_LINE_RAD)).toInt(); val y = (c - r * sin(Meters.SKIN_LINE_RAD)).toInt()
        if (x in 0 until n && y in 0 until n) out[y * n + x] = 0xAAFFC28A.toInt()
    }
    val norm = ln(1f + total / 60f)
    for (i in bins.indices) {
        val v = bins[i]; if (v == 0) continue
        val a = (ln(1f + v) / norm * 255f * 2.4f).toInt().coerceIn(50, 255)
        out[i] = blend(out[i], 0xFF9CFFB0.toInt(), a)
    }
    return Bitmap.createBitmap(out, n, n, Bitmap.Config.ARGB_8888)
}

private fun histogram(h4: Array<IntArray>, w: Int, h: Int): Bitmap {
    val out = IntArray(w * h)
    val colors = intArrayOf(0xFFFF5A5A.toInt(), 0xFF5AE07A.toInt(), 0xFF5AA0FF.toInt(), 0xFFE8E8E8.toInt())
    val max = h4.maxOf { ch -> ch.drop(1).dropLast(1).maxOrNull() ?: 1 }.coerceAtLeast(1)
    for (ch in 0..3) for (x in 0 until w) {
        val bin = x * 256 / w
        val top = h - (h4[ch][bin].toFloat() / max * (h - 1)).toInt().coerceIn(0, h - 1)
        for (y in top until h) out[y * w + x] = blend(out[y * w + x], colors[ch], if (ch == 3) 70 else 110)
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

/** Additive-ish blend of [c] with alpha [a] over [dst]. */
private fun blend(dst: Int, c: Int, a: Int): Int {
    val da = dst ushr 24
    fun ch(s: Int) = (((dst shr s) and 0xFF) + ((c shr s) and 0xFF) * a / 255).coerceAtMost(255)
    return ((da + a).coerceAtMost(255) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
