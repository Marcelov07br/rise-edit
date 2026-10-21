package br.com.riseedit.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.RiseMuted
import br.com.riseedit.core.Clip
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.color.ColorMatch
import br.com.riseedit.core.color.FrameStats
import br.com.riseedit.designsystem.TileSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Source time used to sample a clip: the playhead for the selected clip, else its middle. */
private fun sampleMs(state: EditorState, clip: Clip, selected: Boolean) =
    if (selected) clipSourceMs(state, clip) else clip.sourceAt(clip.durationMs / 2)

/** Interleaved rgb of a bitmap through [map] (argb → argb). */
private fun pixels(b: Bitmap, map: (Int) -> Int = { it }): FloatArray {
    val n = b.width * b.height
    val px = IntArray(n).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
    return FloatArray(n * 3) { i ->
        val p = map(px[i / 3])
        when (i % 3) {
            0 -> (p shr 16 and 0xFF) / 255f
            1 -> (p shr 8 and 0xFF) / 255f
            else -> (
                p and
                    0xFF
                ) /
                255f
        }
    }
}

/** Frame of [clip] rendered with its color (CPU mirror) at the sample time. */
private fun graded(b: Bitmap, clip: Clip, ms: Long): Bitmap {
    val (a, g) = clip.colorAt(ms)
    if (a.isNeutral && g.isIdentity) return b
    val px = IntArray(b.width * b.height).also {
        b.getPixels(it, 0, b.width, 0, 0, b.width, b.height)
    }
    for (i in px.indices) px[i] = br.com.riseedit.color.GradeLuts.applyFull(a, g, px[i])
    return Bitmap.createBitmap(px, b.width, b.height, Bitmap.Config.ARGB_8888)
}

/**
 * "Combinar cor": pick a reference clip (thumbnails), the clips to match (current pre-checked),
 * intensity; solves basic adjustments per target with [ColorMatch] and applies them as normal,
 * editable, undoable color.
 */
@Composable
// detekt: dialog wiring; the math lives in core ColorMatch.
@Suppress("LongMethod")
internal fun ColorMatchDialog(vm: EditorViewModel, state: EditorState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clips = state.project.clips
    val current = state.selected
    var thumbs by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var ref by remember {
        mutableStateOf(
            if (current >
                0
            ) {
                current - 1
            } else {
                (current + 1).coerceAtMost(clips.lastIndex)
            }
        )
    }
    var targets by remember { mutableStateOf(setOf(current)) }
    var intensity by remember { mutableFloatStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(clips.map { it.id }) {
        clips.forEachIndexed { i, c ->
            if (thumbs.containsKey(c.id)) return@forEachIndexed
            val ms = sampleMs(state, c, i == current)
            val b =
                withContext(Dispatchers.IO) {
                    br.com.riseedit.color.grab(context, c.effectiveUri(true), ms, 96)
                }
                    ?.let { withContext(Dispatchers.Default) { graded(it, c, ms) } }
                    ?: return@forEachIndexed
            thumbs = thumbs + (c.id to b)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Combinar cor") },
        text = {
            ColorMatchContent(
                clips, thumbs, ref, targets, intensity, busy,
                onRef = { i ->
                    ref = i
                    targets = targets - i
                },
                onTarget = { i -> targets = if (i in targets) targets - i else targets + i },
                onIntensity = { intensity = it }
            )
        },
        confirmButton = {
            TextButton(enabled = !busy && targets.isNotEmpty() && ref in clips.indices, onClick = {
                busy = true
                scope.launch {
                    val result =
                        withContext(Dispatchers.Default) {
                            solve(context, state, ref, targets, intensity)
                        }
                    busy = false
                    if (result.isEmpty()) {
                        vm.message("Não foi possível ler os quadros para combinar a cor.")
                    } else {
                        vm.applyColorMatch(result, clips[ref].name)
                    }
                    onDismiss()
                }
            }) { Text("Combinar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun solve(
    context: android.content.Context,
    state: EditorState,
    ref: Int,
    targets: Set<Int>,
    intensity: Float
): Map<String, Pair<Long, ColorAdjust>> {
    val clips = state.project.clips
    val r = clips[ref]
    val rms = sampleMs(state, r, ref == state.selected)
    val t0 = System.nanoTime()
    val refFrame =
        br.com.riseedit.color.grab(context, r.effectiveUri(true), rms, 64) ?: return emptyMap()
    val refStats = FrameStats.of(pixels(graded(refFrame, r, rms)))
    val out = LinkedHashMap<String, Pair<Long, ColorAdjust>>()
    for (i in targets) {
        val c = clips.getOrNull(i) ?: continue
        val ms = sampleMs(state, c, i == state.selected)
        val frame = br.com.riseedit.color.grab(context, c.effectiveUri(true), ms, 64) ?: continue
        val (base, grade) = c.colorAt(ms)
        // The adjust stage sees the LUT output when the LUT runs first.
        val raw =
            pixels(frame) {
                if (grade.lutBeforeAdjust) br.com.riseedit.color.GradeLuts.apply(grade, it) else it
            }
        val solved = ColorMatch.solve(raw, base, refStats)
        out[c.id] = ms to ColorMatch.blend(base, solved, intensity)
        android.util.Log.i(
            "RiseColor",
            "match ${c.name} -> ${r.name}: exp=${solved.exposure} con=${solved.contrast} " +
                "temp=${solved.warmth} tint=${solved.tint} sat=${solved.saturation} k=$intensity " +
                "dist ${ColorMatch.distance(
                    ColorMatch.statsAfter(base, raw),
                    refStats
                )} -> ${ColorMatch.distance(ColorMatch.statsAfter(solved, raw), refStats)}"
        )
    }
    android.util.Log.i(
        "RiseColor",
        "match solved ${out.size} clip(s) in ${(System.nanoTime() - t0) / 1_000_000}ms"
    )
    return out
}

@Composable
private fun ClipStrip(
    clips: List<Clip>,
    thumbs: Map<String, Bitmap>,
    selected: (Int) -> Boolean,
    check: Boolean,
    disabled: (Int) -> Boolean = { false },
    onClick: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(clips, key = { _, c -> c.id }) { i, c ->
            val on = selected(i)
            val off = disabled(i)
            Box(
                Modifier.size(
                    64.dp,
                    48.dp
                ).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C1E))
                    .border(
                        if (on) 2.dp else 1.dp,
                        if (on) Color.White else Color.White.copy(alpha = 0.15f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(enabled = !off) { onClick(i) },
                contentAlignment = Alignment.Center
            ) {
                val b = thumbs[c.id]
                if (b != null) {
                    Image(
                        b.asImageBitmap(),
                        c.name,
                        Modifier.size(64.dp, 48.dp),
                        contentScale = ContentScale.Crop,
                        alpha = if (off) 0.3f else 1f
                    )
                } else {
                    Text("${i + 1}", color = RiseMuted, fontSize = 12.sp)
                }
                if (check && on) {
                    Box(
                        Modifier.align(
                            Alignment.TopEnd
                        ).padding(3.dp).size(16.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = Color.Black) }
                }
            }
        }
    }
}

/** Stateless body of [ColorMatchDialog] (Paparazzi renders it directly). */
@Composable
internal fun ColorMatchContent(
    clips: List<Clip>,
    thumbs: Map<String, Bitmap>,
    ref: Int,
    targets: Set<Int>,
    intensity: Float,
    busy: Boolean,
    onRef: (Int) -> Unit,
    onTarget: (Int) -> Unit,
    onIntensity: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Referência (a cor desejada)", color = RiseMuted, fontSize = 12.sp)
        ClipStrip(clips, thumbs, selected = { it == ref }, check = false, onClick = onRef)
        Text("Igualar estes clipes", color = RiseMuted, fontSize = 12.sp)
        ClipStrip(clips, thumbs, selected = {
            it in targets
        }, check = true, disabled = { it == ref }, onClick = onTarget)
        TileSlider("Intensidade", intensity, 0f..1f, default = 1f, format = {
            "%.0f%%".format(
                it * 100f
            )
        }, change = onIntensity)
        Text(
            if (busy) {
                "Analisando quadros…"
            } else {
                "Gera exposição, contraste, temperatura, tint e saturação editáveis em Básico."
            },
            color = RiseMuted,
            fontSize = 12.sp
        )
    }
}
