package br.com.riseedit.text

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorUiState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseOutline
import br.com.riseedit.RiseSurface
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseWhite
import br.com.riseedit.TextTab
import br.com.riseedit.core.TextLayer

/**
 * CapCut-style text editor docked under the preview. Every change updates [EditorUiState.textDraft],
 * which the preview renders live with the export renderer; ✓ commits once (one undo step).
 */
@Composable
internal fun TextEditorSheet(vm: EditorViewModel, ui: EditorUiState, draft: TextLayer) {
    fun update(layer: TextLayer) {
        ui.textDraft = layer
    }
    fun confirm() {
        ui.eyedropper = null
        ui.textDraft = null
        if (draft.text.isBlank()) return
        vm.text(draft)
        vm.selectLayer(draft.id)
    }
    BackHandler { confirm() }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(RiseSurface)
            .navigationBarsPadding()
            .semantics { contentDescription = "Editor de texto" }
    ) {
        TextInputRow(draft, ::update, ::confirm)
        DragHandle(onDismiss = ::confirm)
        TabsRow(ui.textTab) { ui.textTab = it }
        Column(
            Modifier.fillMaxWidth().height(268.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedContent(
                ui.textTab,
                transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
                label = "textTab"
            ) { tab ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (tab) {
                        TextTab.TEMPLATES -> TemplatesTab(draft, ::update)
                        TextTab.FONTS -> FontsTab(draft, ::update)
                        TextTab.STYLES -> StylesTab(draft, ::update, ui)
                        TextTab.EFFECTS -> EffectsTab(draft, ::update)
                        TextTab.ANIMATIONS -> AnimationsTab(draft, ::update)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextInputRow(draft: TextLayer, update: (TextLayer) -> Unit, confirm: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val height by animateDpAsState(if (expanded) 116.dp else 44.dp, label = "input")
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.weight(1f).heightIn(min = height).clip(RoundedCornerShape(14.dp))
                .background(RiseSurfaceHigh).padding(start = 14.dp, end = 4.dp)
        ) {
            BasicTextField(
                draft.text,
                { update(draft.copy(text = it.take(300))) },
                Modifier.align(
                    Alignment.CenterStart
                ).padding(end = 40.dp, top = 10.dp, bottom = 10.dp)
                    .fillMaxWidth().semantics { contentDescription = "Texto" },
                textStyle = TextStyle(color = RiseWhite, fontSize = 16.sp),
                cursorBrush = SolidColor(RiseWhite),
                maxLines = if (expanded) 6 else 2,
                decorationBox = { inner ->
                    if (draft.text.isEmpty()) {
                        Text(
                            "Digite o texto",
                            color = RiseMuted,
                            fontSize = 16.sp
                        )
                    }
                    inner()
                }
            )
            Icon(
                if (expanded) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                if (expanded) "Recolher" else "Expandir",
                Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 6.dp).size(20.dp)
                    .clickable { expanded = !expanded },
                tint = RiseMuted
            )
        }
        Box(
            Modifier.size(
                40.dp
            ).clip(CircleShape).background(RiseWhite).clickable(onClick = confirm)
                .semantics { contentDescription = "Confirmar texto" },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Check, null, Modifier.size(22.dp), tint = RiseBlack) }
    }
}

@Composable
private fun DragHandle(onDismiss: () -> Unit) {
    var drag by remember { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().height(22.dp).pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (drag > 60) onDismiss()
                    drag = 0f
                },
                onVerticalDrag = { _, delta -> drag += delta }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.width(
                36.dp
            ).height(4.dp).clip(RoundedCornerShape(2.dp)).background(RiseOutline)
        )
    }
}

@Composable
private fun TabsRow(selected: TextTab, onSelect: (TextTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        TextTab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onSelect(tab) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    tab.label,
                    color = if (active) RiseWhite else RiseMuted,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
                val width by animateDpAsState(if (active) 18.dp else 0.dp, label = "tab")
                Box(
                    Modifier.padding(top = 5.dp).width(width).height(3.dp)
                        .clip(RoundedCornerShape(2.dp)).background(RiseWhite)
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(RiseOutline))
}
