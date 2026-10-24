package br.com.riseedit.proxy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.RiseAccent
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseOutline
import br.com.riseedit.RiseSurface
import br.com.riseedit.editor.ExportBus
import br.com.riseedit.editor.ProxyBus
import br.com.riseedit.export.RenderBubble

/**
 * "Usar proxies" pill of the preview. [available] = at least one clip has a preview proxy;
 * [generating] = the background job is running.
 */
@Composable
fun ProxyToggle(available: Boolean, on: Boolean, generating: Boolean, modifier: Modifier = Modifier, onToggle: (Boolean) -> Unit) {
    val active = on && available
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape)
            .background(if (active) RiseAccent.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.7f))
            .border(1.dp, if (active) RiseAccent else RiseOutline, shape)
            .clickable(enabled = available) { onToggle(!on) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Layers, null, Modifier.size(16.dp), tint = if (active) RiseAccent else RiseMuted)
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                generating && !available -> "Gerando proxy…"
                !available -> "Sem proxy"
                on -> "Usar proxies"
                else -> "Original"
            },
            fontSize = 13.sp,
            color = if (active) RiseAccent else Color.White
        )
    }
}

/** Expanded bubble: what is being generated + Cancelar. */
@Composable
fun ProxyCard(s: ExportBus.State, onCancel: () -> Unit, onCollapse: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.widthIn(max = 360.dp).clip(RoundedCornerShape(18.dp)).background(RiseSurface)
            .border(1.dp, RiseOutline, RoundedCornerShape(18.dp)).clickable(onClick = onCollapse).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Gerando proxies", color = Color.White, fontSize = 15.sp)
                Text(s.phase, color = RiseMuted, fontSize = 13.sp)
            }
            Text("${(s.progress * 100).toInt()}%", color = Color.White, fontSize = 15.sp)
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, "Cancelar proxies", tint = Color.White) }
        }
        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
        Text("Só para a prévia · a exportação usa o original.", color = RiseMuted, fontSize = 12.sp)
    }
}

/** App-wide: second bubble (lower) while proxies are generated in the background. */
@Composable
fun ProxyHost() {
    val s by ProxyBus.state.collectAsState()
    val expanded by ProxyBus.expanded.collectAsState()
    if (!s.active) return
    if (expanded) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomEnd) {
            ProxyCard(s, onCancel = { ProxyBus.cancel() }, onCollapse = { ProxyBus.expanded.value = false })
        }
    } else {
        RenderBubble(s, initialFromTop = 0.62f) { ProxyBus.expanded.value = true }
    }
}
