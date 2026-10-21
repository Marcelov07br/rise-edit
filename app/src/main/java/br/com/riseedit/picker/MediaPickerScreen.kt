package br.com.riseedit.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.riseedit.RiseAccent
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseSurface
import br.com.riseedit.RiseWhite
import br.com.riseedit.designsystem.PanelIconButton
import br.com.riseedit.designsystem.SegmentedTabs
import br.com.riseedit.riseSpring
import br.com.riseedit.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stateless picker: pill tabs, 3-column grid, tap-order badges, Adicionar bar. */
@Suppress("LongMethod") // single Compose screen body
@Composable
fun MediaPickerScreen(
    tab: GalleryTab,
    items: List<GalleryItem>,
    selected: List<Uri>,
    access: GalleryAccess,
    imageSeconds: Int,
    onTab: (GalleryTab) -> Unit,
    onToggle: (GalleryItem) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onRequest: () -> Unit,
    thumb: @Composable (GalleryItem) -> Unit
) {
    Column(Modifier.fillMaxSize().background(RiseBlack).padding(top = 12.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", onClick = onBack)
            Text(
                "Adicionar mídia",
                color = RiseWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        SegmentedTabs(GalleryTab.entries, tab, Modifier.padding(12.dp), label = {
            it.label
        }, minTab = 120.dp, onSelect = onTab)
        if (access ==
            GalleryAccess.PARTIAL
        ) {
            Banner("Acesso só às mídias escolhidas.", "Escolher mais", onRequest)
        }
        if (tab == GalleryTab.IMAGES) {
            Text(
                "Cada foto vira um clipe de $imageSeconds s (Configurações › Projeto).",
                color = RiseMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                access == GalleryAccess.NONE -> Empty(
                    "Permita o acesso às fotos e vídeos para escolher daqui.",
                    "Permitir",
                    onRequest
                )
                items.isEmpty() -> Empty(
                    if (tab ==
                        GalleryTab.VIDEOS
                    ) {
                        "Nenhum vídeo encontrado."
                    } else {
                        "Nenhuma imagem encontrada."
                    },
                    null,
                    onRequest
                )
                else -> LazyVerticalGrid(
                    GridCells.Fixed(3),
                    Modifier.fillMaxSize().padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(items, key = { it.uri.toString() }) { item ->
                        Tile(item, PickerSelection.order(selected, item.uri), {
                            onToggle(item)
                        }) { thumb(item) }
                    }
                }
            }
        }
        val enabled = selected.isNotEmpty()
        Box(
            Modifier.fillMaxWidth().padding(12.dp).height(56.dp).clip(RoundedCornerShape(16.dp))
                .background(if (enabled) RiseAccent else RiseSurface)
                .clickable(enabled = enabled, onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (enabled) "Adicionar (${selected.size})" else "Selecione mídias",
                color = if (enabled) RiseBlack else RiseMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun Tile(
    item: GalleryItem,
    order: Int?,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (order != null) 0.9f else 1f, riseSpring(), label = "tile")
    Box(
        Modifier.aspectRatio(1f).background(RiseSurface).clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    ) {
        Box(
            Modifier.fillMaxSize().scale(scale).clip(
                RoundedCornerShape(
                    if (order !=
                        null
                    ) {
                        10.dp
                    } else {
                        0.dp
                    }
                )
            )
        ) { content() }
        if (item.video) {
            Text(
                PickerSelection.duration(item.durationMs),
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .background(
                        Color.Black.copy(alpha = 0.55f),
                        RoundedCornerShape(6.dp)
                    ).padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape)
                .background(if (order != null) RiseAccent else Color.Black.copy(alpha = 0.25f))
                .border(1.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (order !=
                null
            ) {
                Text(
                    "$order",
                    color = RiseBlack,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(
            horizontal = 12.dp,
            vertical = 4.dp
        ).clip(RoundedCornerShape(12.dp))
            .background(RiseSurface).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = RiseMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            action,
            color = RiseAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}

@Composable
private fun Empty(text: String, action: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = RiseMuted, fontSize = 14.sp)
        if (action != null) {
            Box(
                Modifier.padding(
                    top = 16.dp
                ).clip(
                    RoundedCornerShape(14.dp)
                ).background(RiseAccent).clickable(onClick = onClick)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) { Text(action, color = RiseBlack, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Full-screen picker with permissions + MediaStore; returns URIs in tap order. */
@Composable
fun MediaPickerRoute(onPick: (List<Uri>) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(Gallery.access(context)) }
    var tab by rememberSaveable { mutableStateOf(GalleryTab.VIDEOS) }
    var selected by remember { mutableStateOf(listOf<Uri>()) }
    var reload by remember { mutableStateOf(0) }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            access = Gallery.access(context)
            reload++
        }
    LaunchedEffect(Unit) { if (access == GalleryAccess.NONE) ask.launch(Gallery.permissions()) }
    val items by produceState(emptyList<GalleryItem>(), tab, access, reload) {
        value =
            if (access ==
                GalleryAccess.NONE
            ) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) { Gallery.query(context, tab) }
            }
    }
    Dialog(
        onClose,
        DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        MediaPickerScreen(
            tab,
            items,
            selected,
            access,
            (SettingsStore.current.imageDurationMs / 1000).toInt(),
            { tab = it },
            { selected = PickerSelection.toggle(selected, it.uri) },
            { onPick(selected) },
            onClose,
            { ask.launch(Gallery.permissions()) }
        ) { item ->
            val bmp by produceState<android.graphics.Bitmap?>(null, item.uri) {
                value = withContext(Dispatchers.IO) { Gallery.thumbnail(context, item) }
            }
            bmp?.let {
                Image(
                    it.asImageBitmap(),
                    item.name,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

/** Set by "Criar e escolher mídia" so a new project opens straight on the picker. */
object PickerRequest {
    var pending = false
}
