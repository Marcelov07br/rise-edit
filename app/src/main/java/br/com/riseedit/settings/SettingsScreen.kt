package br.com.riseedit.settings

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.Accent
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseOutline
import br.com.riseedit.RiseSurface
import br.com.riseedit.RiseWhite
import br.com.riseedit.ThemeMode
import br.com.riseedit.core.Aspect
import br.com.riseedit.core.ProjectFormat
import br.com.riseedit.core.Resolution
import br.com.riseedit.core.TvStandard
import br.com.riseedit.core.VideoCodec
import br.com.riseedit.designsystem.PanelIconButton
import br.com.riseedit.designsystem.RiseDropdown
import br.com.riseedit.designsystem.RiseToggle
import br.com.riseedit.designsystem.SegmentedTabs
import br.com.riseedit.designsystem.riseSafeArea
import br.com.riseedit.preview.PreviewQuality

enum class SettingsSection(val label: String) {
    UPDATES("Atualizações"),
    LOOK("Aparência"),
    PROJECT("Projeto"),
    EXPORT("Exportação"),
    PREVIEW("Prévia"),
    TIMELINE("Timeline"),
    ABOUT("Sobre")
}

/** Side effects of the settings screen (wired in MainActivity, no-ops in shots). */
class SettingsActions(
    val back: () -> Unit = {},
    val pickFolder: () -> Unit = {},
    val clearCache: () -> Unit = {},
    val shareLogs: () -> Unit = {}
)

/** Read-only facts shown on the screen. */
class SettingsInfo(
    val versionName: String,
    val versionCode: Long,
    val cacheBytes: Long = 0,
    val folderLabel: String? = null
)

internal val LICENSES = listOf(
    "AndroidX Media3 (ExoPlayer, Transformer)" to "Apache 2.0",
    "Jetpack Compose · Material Icons" to "Apache 2.0",
    "AndroidX DataStore" to "Apache 2.0",
    "Kotlin · kotlinx.coroutines" to "Apache 2.0",
    "OpenCV" to "Apache 2.0",
    "whisper.cpp (legendas)" to "MIT"
)

/** 0.19: Configurações as its own screen: pill sections on top, grouped rows below. */
@Composable
fun SettingsScreen(
    s: AppSettings,
    section: SettingsSection,
    onSection: (SettingsSection) -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    info: SettingsInfo,
    actions: SettingsActions = SettingsActions()
) {
    Column(
        Modifier.fillMaxSize().background(RiseBlack).riseSafeArea().padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", onClick = actions.back)
            Text(
                "Configurações",
                color = RiseWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        SegmentedTabs(SettingsSection.entries, section, label = {
            it.label
        }, minTab = 104.dp, onSelect = onSection)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (section) {
                SettingsSection.UPDATES -> Updates(info, actions)
                SettingsSection.LOOK -> Look(s, onChange)
                SettingsSection.PROJECT -> ProjectDefaults(s, onChange)
                SettingsSection.EXPORT -> Export(s, onChange, info, actions)
                SettingsSection.PREVIEW -> Preview(s, onChange, info, actions)
                SettingsSection.TIMELINE -> Timeline(s, onChange)
                SettingsSection.ABOUT -> About(info, actions)
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), color = RiseMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RiseSurface)
            .border(1.dp, RiseOutline, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) { content() }
}

@Composable
private fun Row2(title: String, hint: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = RiseWhite, fontSize = 15.sp)
            hint?.let { Text(it, color = RiseMuted, fontSize = 12.sp) }
        }
        trailing()
    }
}

@Composable
private fun Toggle(title: String, hint: String?, value: Boolean, set: (Boolean) -> Unit) =
    Row2(title, hint) { RiseToggle(value, onChange = set) }

@Composable
private fun Action(icon: ImageVector, title: String, hint: String?, onClick: () -> Unit) =
    Row2(title, hint) { PanelIconButton(icon, title, onClick = onClick) }

@Composable
private fun <T> Pair2(
    a: Pair<String, Triple<List<T>, T, (T) -> Unit>>,
    b: Pair<String, Triple<List<*>, Any?, (Any?) -> Unit>>? = null,
    text: (Any?) -> String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RiseDropdown(a.first, a.second.first, a.second.second, Modifier.weight(1f), text = {
            text(it)
        }) {
            a.second.third(it)
        }
        if (b != null) {
            @Suppress("UNCHECKED_CAST")
            val opts = b.second.first as List<Any?>
            RiseDropdown(b.first, opts, b.second.second, Modifier.weight(1f), text = {
                text(it)
            }) { b.second.third(it) }
        }
    }
}

private fun label(v: Any?): String = when (v) {
    is ThemeMode -> v.label
    is Aspect -> v.label
    is Resolution -> v.label
    is TvStandard -> v.label
    is br.com.riseedit.core.FrameRateChoice -> v.label
    is VideoCodec -> v.label
    is Quality -> v.label
    is PreviewQuality -> v.label
    is ThumbDensity -> v.label
    is Long -> "${v / 1000} s"
    else -> v.toString()
}

@Composable
private fun Updates(info: SettingsInfo, actions: SettingsActions) {
    Group("Atualizações") {
        Row2("Versão instalada", "Rise Edit ${info.versionName} (${info.versionCode})") {}
    }
}

@Composable
private fun Look(s: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    Group("Tema") {
        Pair2(
            "Tema" to
                Triple(ThemeMode.entries, s.theme) { v: ThemeMode ->
                    onChange { it.copy(theme = v) }
                },
            text = ::label
        )
        Text("Cor de destaque", color = RiseWhite, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Accent.entries.forEach { a ->
                val on = a == s.accent
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(a.color)
                        .border(
                            if (on) 3.dp else 1.dp,
                            if (on) RiseWhite else RiseOutline,
                            CircleShape
                        )
                        .clickable(role = Role.RadioButton) { onChange { it.copy(accent = a) } },
                    contentAlignment = Alignment.Center
                ) {
                    if (on) {
                        Icon(
                            Icons.Rounded.Check,
                            a.label,
                            Modifier.size(18.dp),
                            tint = androidx.compose.ui.graphics.Color.Black
                        )
                    }
                }
            }
        }
        Text(s.accent.label, color = RiseMuted, fontSize = 12.sp)
    }
    Group("Interação") {
        Toggle("Háptico", "Vibração leve em sliders, alças e seleção", s.haptics) { v ->
            onChange { it.copy(haptics = v) }
        }
        Toggle("Reduzir animações", "Molas rápidas, sem balanço", s.reduceMotion) { v ->
            onChange { it.copy(reduceMotion = v) }
        }
    }
}

@Composable
private fun ProjectDefaults(s: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    Group("Novo projeto começa com") {
        FormatFields(s.aspect, s.resolution, s.standard, s.rate) { a, r, st, rate ->
            onChange { it.copy(aspect = a, resolution = r, standard = st, rate = rate) }
        }
        Pair2(
            "Imagem no timeline" to
                Triple(AppSettings.IMAGE_DURATIONS, s.imageDurationMs) { v: Long ->
                    onChange { it.copy(imageDurationMs = v) }
                },
            text = ::label
        )
    }
}

/** Shared by Configurações › Projeto and Criar projeto. fps choices follow the standard. */
@Composable
fun FormatFields(
    aspect: Aspect,
    resolution: Resolution,
    standard: TvStandard,
    rate: br.com.riseedit.core.FrameRateChoice,
    expandedFps: Boolean = false,
    change: (Aspect, Resolution, TvStandard, br.com.riseedit.core.FrameRateChoice) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RiseDropdown("Proporção", Aspect.entries, aspect, Modifier.weight(1f), text = {
            it.label
        }) {
            change(it, resolution, standard, rate)
        }
        RiseDropdown("Resolução", Resolution.entries, resolution, Modifier.weight(1f), text = {
            it.label
        }) {
            change(aspect, it, standard, rate)
        }
    }
    SegmentedTabs(TvStandard.entries, standard, label = { it.label }) {
        change(aspect, resolution, it, ProjectFormat.coerce(rate, it))
    }
    RiseDropdown(
        "Quadros por segundo (${standard.label})",
        ProjectFormat.rates(standard),
        ProjectFormat.coerce(rate, standard),
        expandedInitially = expandedFps,
        text = { it.label }
    ) { change(aspect, resolution, standard, it) }
}

@Composable
private fun Export(
    s: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    info: SettingsInfo,
    actions: SettingsActions
) {
    Group("Padrão de exportação") {
        Pair2(
            "Codec" to
                Triple(VideoCodec.entries, s.codec) { v: VideoCodec ->
                    onChange { it.copy(codec = v) }
                },
            "Qualidade (bitrate)" to
                Triple(Quality.entries, s.quality) { v: Any? ->
                    onChange { it.copy(quality = v as Quality) }
                },
            text = ::label
        )
        Text(
            "Bitrate sugerido: ${s.exportDefaults().bitrate / 1_000_000} Mbps em ${s.resolution.label} ${s.rate.label}",
            color = RiseMuted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
    Group("Destino") {
        Action(
            Icons.Rounded.FolderOpen,
            "Pasta destino",
            info.folderLabel ?: "Perguntar a cada exportação",
            actions.pickFolder
        )
        if (s.exportFolder != null) {
            Action(Icons.Rounded.DeleteSweep, "Voltar a perguntar", null) {
                onChange { it.copy(exportFolder = null) }
            }
        }
    }
}

@Composable
private fun Preview(
    s: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    info: SettingsInfo,
    actions: SettingsActions
) {
    Group("Prévia") {
        Pair2(
            "Prévia padrão" to
                Triple(PreviewQuality.entries, s.previewQuality) { v: PreviewQuality ->
                    onChange { it.copy(previewQuality = v) }
                },
            text = ::label
        )
        Toggle(
            "Proxy automático acima de 1080p",
            "Gera uma cópia leve para a prévia de vídeos 4K/1440p (o export usa o original)",
            s.autoProxy4k
        ) { v -> onChange { it.copy(autoProxy4k = v) } }
    }
    Group("Armazenamento") {
        Action(
            Icons.Rounded.DeleteSweep,
            "Limpar cache",
            "%.1f MB · proxies em uso pelos projetos são mantidos".format(
                info.cacheBytes / 1_048_576.0
            ),
            actions.clearCache
        )
    }
}

@Composable
private fun Timeline(s: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    Group("Timeline") {
        Toggle("Ímã (snap)", "Ao abrir um projeto", s.snap) { v -> onChange { it.copy(snap = v) } }
        Toggle("Ripple ao excluir", "Fecha espaços em todas as faixas", s.ripple) { v ->
            onChange { it.copy(ripple = v) }
        }
        Pair2(
            "Miniaturas" to
                Triple(ThumbDensity.entries, s.thumbs) { v: ThumbDensity ->
                    onChange { it.copy(thumbs = v) }
                },
            text = ::label
        )
    }
}

@Composable
private fun About(info: SettingsInfo, actions: SettingsActions) {
    Group("Sobre") {
        Row2("Rise Edit", "Versão ${info.versionName} (${info.versionCode})") {}
        Action(
            Icons.Rounded.Share,
            "Compartilhar registro de erros",
            "Falhas e saídas recentes do app",
            actions.shareLogs
        )
    }
    Group("Licenças") {
        LICENSES.forEach { (name, license) ->
            Row(Modifier.fillMaxWidth()) {
                Text(name, color = RiseWhite, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(license, color = RiseMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
