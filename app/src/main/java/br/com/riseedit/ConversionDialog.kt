package br.com.riseedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.riseedit.designsystem.GraphicSurface

/** What the dialog shows; [progress] null = offer, otherwise converting (0..1). */
data class ConversionUi(
    val count: Int,
    val reason: String,
    val tenBitOutput: Boolean,
    val progress: Float? = null,
    val phase: String = ""
)

/** 0.21 "Converter para edição" — rounded dark card; offer and progress states. */
@Composable
fun ConversionContent(ui: ConversionUi, onConvert: () -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(RiseSurfaceHigh)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            if (ui.progress == null) "Converter para edição?" else "Convertendo para edição",
            color = RiseWhite, fontSize = 19.sp, fontWeight = FontWeight.SemiBold
        )
        Text(ui.reason, color = RiseMuted, fontSize = 14.sp)
        GraphicSurface {
            Row2("Arquivos", if (ui.count == 1) "1 vídeo" else "${ui.count} vídeos")
            Row2("Saída", if (ui.tenBitOutput) "HEVC 10 bits · 4:2:0" else "H.264 8 bits · 4:2:0")
            Row2("Mantém", "resolução, fps, rotação, cor")
            Row2("Original", "preservado, intacto")
        }
        if (ui.progress != null) {
            LinearProgressIndicator(
                progress = { ui.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                color = RiseWhite,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ui.phase, color = RiseMuted, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancelar", color = Color(0xFFFF8A80)) }
            }
        } else {
            Text(
                "A conversão roda no aparelho e pode levar alguns minutos. Dá para cancelar a qualquer momento.",
                color = RiseMuted, fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Agora não", color = RiseMuted) }
                Button(
                    onClick = onConvert,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = RiseWhite, contentColor = RiseBlack)
                ) { Text("Converter para edição") }
            }
        }
    }
}

@Composable
private fun Row2(label: String, value: String) = Row {
    Text(label, color = RiseMuted, fontSize = 13.sp)
    Spacer(Modifier.weight(1f))
    Text(value, color = RiseWhite, fontSize = 13.sp)
}

@Composable
fun ConversionDialog(ui: ConversionUi, onConvert: () -> Unit, onCancel: () -> Unit) = Dialog(
    onDismissRequest = { if (ui.progress == null) onCancel() },
    properties = DialogProperties(dismissOnBackPress = ui.progress == null, dismissOnClickOutside = false)
) { ConversionContent(ui, onConvert, onCancel) }
