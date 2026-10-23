package br.com.riseedit.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.ConversionContent
import br.com.riseedit.ConversionUi
import br.com.riseedit.RiseTheme
import br.com.riseedit.core.ChromaFormat
import br.com.riseedit.core.MediaFault
import org.junit.Rule
import org.junit.Test

class ConversionShots {
    @get:Rule val p =
        Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    private val reason = MediaFault.UnsupportedChroma(ChromaFormat.YUV422, 10, "video/avc").userMessage

    @Composable
    private fun Scrim(content: @Composable () -> Unit) = RiseTheme {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).padding(24.dp), Alignment.Center) {
            content()
        }
    }

    @Test fun offerTenBit() = p.snapshot {
        Scrim { ConversionContent(ConversionUi(1, reason, tenBitOutput = true), {}, {}) }
    }

    @Test fun offerEightBit() = p.snapshot {
        Scrim { ConversionContent(ConversionUi(3, reason, tenBitOutput = false), {}, {}) }
    }

    @Test fun converting() = p.snapshot {
        Scrim {
            ConversionContent(
                ConversionUi(1, "O vídeo está sendo copiado num formato que este aparelho edita.", true, 0.42f, "Convertendo 1/1 · 42%"),
                {}, {}
            )
        }
    }
}
