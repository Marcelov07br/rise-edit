package br.com.riseedit.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.riseedit.MediaAssets
import br.com.riseedit.core.AudioClip
import br.com.riseedit.core.MediaFault
import kotlin.math.max

@Composable
internal fun TimelineWaveform(audio: AudioClip) {
    val context = LocalContext.current
    var samples by remember(audio.uri) { mutableStateOf<List<Float>?>(null) }
    var fault by remember(audio.uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(audio.uri) {
        try {
            samples = MediaAssets.waveform(context, audio.uri, audio.sourceDurationMs)
            if (samples == null) fault = "Arquivo sem faixa de áudio"
        } catch (e: MediaFault) {
            fault = e.userMessage
        }
    }
    fault?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 2) }
    var widthPx by remember { mutableStateOf(0f) }
    val level = br.com.riseedit.core.audio.WavePeaks.level(widthPx)
    // Peaks recomputed only when the samples, trim or zoom LEVEL change; draws reuse them.
    val peaks = remember(samples, audio.sourceDurationMs, audio.startMs, audio.endMs, level) {
        samples?.let {
            br.com.riseedit.core.audio.WavePeaks.peaks(
                it.toFloatArray(), audio.sourceDurationMs, audio.startMs, audio.endMs, level
            )
        }
    }
    Canvas(
        Modifier.fillMaxSize().padding(vertical = 7.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
    ) {
        val p = peaks ?: return@Canvas
        val step = size.width / p.size
        for (i in p.indices) {
            val x = (i + 0.5f) * step
            val height = max(1f, p[i] * size.height * 0.45f)
            drawLine(
                Color(0xFFBDBDBD),
                Offset(x, size.height / 2 - height),
                Offset(x, size.height / 2 + height),
                2f
            )
        }
    }
}
