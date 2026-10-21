package br.com.riseedit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.core.Aspect
import br.com.riseedit.core.FrameRateChoice
import br.com.riseedit.core.Resolution
import br.com.riseedit.core.TvStandard
import br.com.riseedit.designsystem.GraphicSurface
import br.com.riseedit.designsystem.HierarchyTokens
import br.com.riseedit.designsystem.PanelIconButton
import br.com.riseedit.settings.FormatFields

/** 0.19 "Criar projeto" form state (pre-filled from Configurações › Projeto). */
data class NewProjectForm(
    val name: String,
    val aspect: Aspect,
    val resolution: Resolution,
    val standard: TvStandard,
    val rate: FrameRateChoice
)

@Suppress("LongMethod") // single Compose screen body
@Composable
fun NewProjectScreen(
    form: NewProjectForm,
    onChange: (NewProjectForm) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    expandedFps: Boolean = false
) {
    Column(
        Modifier.fillMaxSize().background(
            RiseBlack
        ).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", onClick = onBack)
            Text(
                "Criar projeto",
                color = RiseWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        val t = HierarchyTokens
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(t.FieldRadius)).background(t.FieldBg)
                .border(
                    1.dp,
                    t.FieldBorder,
                    RoundedCornerShape(t.FieldRadius)
                ).padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("Nome", color = RiseMuted, fontSize = t.FieldLabel)
            BasicTextField(
                form.name,
                { onChange(form.copy(name = it.take(80))) },
                singleLine = true,
                textStyle = TextStyle(color = RiseWhite, fontSize = 16.sp),
                cursorBrush = SolidColor(RiseAccent),
                modifier = Modifier.fillMaxWidth()
            )
        }
        GraphicSurface {
            Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                val r = form.aspect.ratio.takeIf { it > 0f } ?: (16f / 9f)
                val w = if (r >= 1f) 220f else 120f * r
                val h = if (r >= 1f) 220f / r else 120f
                Box(
                    Modifier.size(w.dp, h.coerceAtMost(130f).dp).clip(RoundedCornerShape(6.dp))
                        .border(2.dp, RiseAccent, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${form.aspect.label}\n${form.resolution.label} · ${form.rate.label}",
                        color = RiseWhite,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        FormatFields(form.aspect, form.resolution, form.standard, form.rate, expandedFps) {
                a,
                r,
                s,
                rate
            ->
            onChange(form.copy(aspect = a, resolution = r, standard = s, rate = rate))
        }
        Box(
            Modifier.fillMaxWidth().height(
                56.dp
            ).clip(RoundedCornerShape(16.dp)).background(RiseAccent)
                .clickable(onClick = onCreate),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Criar e escolher mídia",
                color = RiseBlack,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}
