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
    Canvas(Modifier.fillMaxSize().padding(vertical = 7.dp)) {
        samples?.forEachIndexed { i, value ->
            val source = audio.sourceDurationMs * i.toFloat() / requireNotNull(samples).size
            if (source >= audio.startMs && source <= audio.endMs) {
                val x = (source - audio.startMs) / audio.durationMs * size.width
                val height = max(1f, value * size.height * 0.45f)
                drawLine(
                    Color(0xFFBDBDBD),
                    Offset(x, size.height / 2 - height),
                    Offset(x, size.height / 2 + height),
                    2f
                )
            }
        }
    }
}
