package br.com.riseedit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.VideoLibrary
import br.com.riseedit.core.TextLayer
import br.com.riseedit.tools.EditorTool
import kotlin.math.min

/** Bottom toolbar content: leading back button (when nested) plus the current level's items. */
internal class ToolbarLevel(val leading: ToolbarItem?, val items: List<ToolbarItem>)

private enum class Base { ROOT, CLIP, TEXT, OVERLAY, AUDIO }

private fun base(state: EditorState, ui: EditorUiState): Base {
    val id = state.selection
    val project = state.project
    return when {
        project.clips.isEmpty() -> Base.ROOT
        id != null && project.texts.any { it.id == id } -> Base.TEXT
        id != null && project.overlays.any { it.id == id } -> Base.OVERLAY
        id != null && project.audio.any { it.id == id } -> Base.AUDIO
        ui.contextual -> Base.CLIP
        else -> Base.ROOT
    }
}

internal fun toolbarLevel(
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUiState,
    media: EditorMediaActions
): ToolbarLevel {
    val enabled = state.busy == null
    val nested = ui.menu.lastOrNull()
    val base = base(state, ui)
    val items = if (nested != null) {
        menuItems(nested, state, ui, media, enabled)
    } else {
        baseItems(base, vm, state, ui, media, enabled)
    }
    val back = if (nested != null || base != Base.ROOT) {
        ToolbarItem("Voltar", Icons.AutoMirrored.Outlined.ArrowBack) { ui.back(vm) }
    } else {
        null
    }
    return ToolbarLevel(back, items)
}

/** Pops one level: closes a panel, then a sub-toolbar, then the selection. */
internal fun EditorUiState.back(vm: EditorViewModel) {
    when {
        tool != null -> tool = null
        menu.isNotEmpty() -> menu = menu.dropLast(1)
        else -> {
            contextual = false
            vm.clearLayer()
        }
    }
}

private fun EditorUiState.push(menu: ToolMenu) {
    tool = null
    this.menu = this.menu + menu
}

internal fun EditorUiState.openTool(tool: EditorTool) {
    lastTool = tool
    this.tool = if (this.tool == tool) null else tool
}

private fun EditorUiState.toolItem(tool: EditorTool, enabled: Boolean, label: String = tool.label) =
    ToolbarItem(label, tool.icon, enabled, active = this.tool == tool) { openTool(tool) }

private fun baseItems(
    base: Base,
    vm: EditorViewModel,
    state: EditorState,
    ui: EditorUiState,
    media: EditorMediaActions,
    enabled: Boolean
): List<ToolbarItem> {
    val delete = ToolbarItem("Excluir", Icons.Outlined.DeleteOutline, enabled) {
        if (state.selection != null) vm.removeLayer() else vm.remove()
        ui.tool = null
        ui.contextual = false
    }
    return when (base) {
        Base.ROOT -> listOf(
            ToolbarItem("Vídeo", Icons.Outlined.Add, enabled, onClick = media.videos),
            ui.toolItem(EditorTool.Stabilize, enabled),
            ui.toolItem(EditorTool.Trim, enabled),
            ToolbarItem("Texto", Icons.Outlined.TextFields, enabled) { ui.push(ToolMenu.TEXT) },
            ToolbarItem("Áudio", Icons.Outlined.MusicNote, enabled) { ui.push(ToolMenu.AUDIO) },
            ToolbarItem("Overlay", Icons.Outlined.FilterNone, enabled) {
                ui.push(ToolMenu.OVERLAY)
            },
            ToolbarItem("Cor", Icons.Outlined.Palette, enabled) { ui.push(ToolMenu.COLOR) },
            ui.toolItem(EditorTool.Format, enabled)
        )
        Base.CLIP -> listOf(
            ToolbarItem("Dividir", Icons.Outlined.VerticalSplit, enabled, onClick = vm::split),
            ui.toolItem(EditorTool.Speed, enabled),
            ui.toolItem(EditorTool.Volume, enabled),
            ui.toolItem(EditorTool.Stabilize, enabled),
            ui.toolItem(EditorTool.Trim, enabled),
            ToolbarItem("Cor", Icons.Outlined.Palette, enabled) { ui.push(ToolMenu.COLOR) },
            ui.toolItem(EditorTool.ClipAnimation, enabled),
            delete
        )
        Base.TEXT -> {
            val layer = state.project.texts.first { it.id == state.selection }
            listOf(
                ToolbarItem("Editar", Icons.Outlined.Edit, enabled) {
                    ui.editText(layer, TextTab.STYLES)
                }
            ) + textTabItems(ui, enabled) { layer } +
                listOf(ui.toolItem(EditorTool.TextTiming, enabled), delete)
        }
        Base.OVERLAY -> listOf(
            ui.toolItem(EditorTool.OverlayOpacity, enabled),
            ui.toolItem(EditorTool.OverlayBlend, enabled),
            ui.toolItem(EditorTool.OverlayMask, enabled),
            ui.toolItem(EditorTool.OverlayTrim, enabled),
            delete
        )
        Base.AUDIO -> listOf(ui.toolItem(EditorTool.AudioLayer, enabled, "Ajustar"), delete)
    }
}

private fun menuItems(
    menu: ToolMenu,
    state: EditorState,
    ui: EditorUiState,
    media: EditorMediaActions,
    enabled: Boolean
): List<ToolbarItem> = when (menu) {
    ToolMenu.TEXT -> listOf(
        ToolbarItem("Adicionar texto", Icons.Outlined.AddBox, enabled) {
            ui.editText(newText(state), TextTab.STYLES)
        }
    ) + textTabItems(ui, enabled) {
        state.project.texts.firstOrNull { it.id == state.selection } ?: newText(state)
    }
    ToolMenu.AUDIO -> listOf(
        ToolbarItem("Música", Icons.Outlined.LibraryMusic, enabled, onClick = media.audio),
        ToolbarItem("Efeitos sonoros", Icons.Outlined.GraphicEq, enabled, onClick = media.audio),
        ui.toolItem(EditorTool.Record, enabled),
        ToolbarItem("Extrair", Icons.Outlined.VideoLibrary, enabled, onClick = media.extract)
    )
    ToolMenu.OVERLAY -> listOf(
        ToolbarItem("Adicionar overlay", Icons.Outlined.AddBox, enabled, onClick = media.overlay),
        ui.toolItem(EditorTool.OverlayOpacity, enabled),
        ui.toolItem(EditorTool.OverlayBlend, enabled),
        ui.toolItem(EditorTool.OverlayMask, enabled)
    )
    ToolMenu.COLOR -> listOf(
        ui.toolItem(EditorTool.Adjust, enabled),
        ui.toolItem(EditorTool.Filters, enabled)
    )
}

private fun textTabItems(
    ui: EditorUiState,
    enabled: Boolean,
    target: () -> TextLayer
): List<ToolbarItem> {
    val icons = mapOf(
        TextTab.TEMPLATES to Icons.Outlined.SpaceDashboard,
        TextTab.FONTS to Icons.Outlined.FontDownload,
        TextTab.STYLES to Icons.Outlined.BorderColor,
        TextTab.EFFECTS to Icons.Outlined.AutoAwesome,
        TextTab.ANIMATIONS to Icons.Outlined.Animation
    )
    return TextTab.entries.map { tab ->
        ToolbarItem(tab.label, icons.getValue(tab), enabled) { ui.editText(target(), tab) }
    }
}

internal fun EditorUiState.editText(layer: TextLayer, tab: TextTab) {
    tool = null
    textTab = tab
    textDraft = layer
}

private fun newText(state: EditorState): TextLayer {
    val project = state.project
    val start = state.positionMs.coerceIn(0, maxOf(0, project.durationMs - 1))
    val end = min(project.durationMs, start + 3000).coerceAtLeast(start + 1)
    return TextLayer(text = "", startMs = start, endMs = end, y = 0f)
}
