package br.com.riseedit.tools

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorState
import br.com.riseedit.EditorViewModel
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseMuted
import br.com.riseedit.RiseOutline
import br.com.riseedit.RiseSurfaceHigh
import br.com.riseedit.RiseWhite
import br.com.riseedit.color.PausedColorRenderer
import br.com.riseedit.color.grab
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.ColorAdjustMath
import br.com.riseedit.core.color.FilterPreset
import br.com.riseedit.core.color.FilterRegistry
import br.com.riseedit.designsystem.ChannelChips
import br.com.riseedit.designsystem.TileSlider
import br.com.riseedit.riseSpring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val THUMB_PX = 160
private const val ALL = "Todos"

/** Filter thumbnails cached by clip/frame/look so reopening the panel is instant. */
private object FilterThumbCache {
    val cache = LruCache<String, Map<String, ImageBitmap>>(6)
}

/**
 * Filtros (CapCut-like): horizontal carousel of boxes, each showing the clip's current frame with the
 * filter applied by the export shader ([PausedColorRenderer.thumbnails] → ColorPass), ~160px,
 * generated off the main thread and cached. Fallback: built-in sample colored by the CPU mirror.
 */
@Composable
// detekt: thumbnail cache/fallback paths.
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun FiltersPanel(vm: EditorViewModel, state: EditorState) {
    val clip = state.project.clips.getOrNull(state.selected) ?: return
    val context = LocalContext.current
    UserPresetStore.load(context)
    val users = UserPresetStore.presets.toList()
    val presets =
        FilterRegistry.all + users.map { FilterPreset(USER + it.id, it.name, it.adjust, MINE) }
    var managing by remember { mutableStateOf<br.com.riseedit.core.color.UserPreset?>(null) }
    // Frame of the clip when the panel opened; the rest of the clip's look (exposure, curves, LUT…)
    // is kept in the thumbnail so it shows what the clip will really look like.
    val sourceMs = remember(clip.id) {
        if (vm.previewClip?.id ==
            clip.id
        ) {
            vm.previewSourceMs
        } else {
            clip.startMs
        }
    }
    val base = clip.adjustments
    val grade = clip.grade
    val uri = clip.effectiveUri(true)
    fun lookOf(p: FilterPreset): ColorAdjust =
        users.firstOrNull { USER + it.id == p.id }?.applyTo(base) ?: base.withFilter(p.adjust)
    val cacheKey = "$uri@$sourceMs|$base|$grade|${presets.map {
        it.id + it.adjust.hashCode()
    }.hashCode()}"
    // Cached → shown at once; otherwise rendered once per key (LaunchedEffect + state instead of
    // produceState, whose lint check rejects the conditional assignment).
    val thumbsState = remember(cacheKey) { mutableStateOf(FilterThumbCache.cache.get(cacheKey)) }
    LaunchedEffect(cacheKey) {
        if (thumbsState.value != null) return@LaunchedEffect
        val frame = withContext(Dispatchers.IO) { grab(context, uri, sourceMs, THUMB_PX) }
        val rendered = frame?.let { f ->
            PausedColorRenderer.thumbnails(f, presets.map(::lookOf), grade)
        }
        val map = if (rendered != null) {
            presets.zip(rendered).associate { (p, b) -> p.id to b.asImageBitmap() }
        } else {
            withContext(Dispatchers.Default) { sampleThumbnails(presets, base, ::lookOf) }
        }
        FilterThumbCache.cache.put(cacheKey, map)
        thumbsState.value = map
    }
    val thumbs = thumbsState.value
    val match = FilterRegistry.match(base)
    val userMatch = users.firstOrNull { !it.owned().isNeutral && it.matches(base) }
    FiltersContent(
        presets = presets,
        selectedId = if (userMatch != null &&
            (match == null || match.preset.isNone)
        ) {
            USER + userMatch.id
        } else {
            match?.preset?.id
        },
        intensity = match?.intensity ?: 1f,
        thumbnail = { thumbs?.get(it.id) },
        onSelect = { p -> vm.clipSettings(clip.copy(adjustments = lookOf(p))) },
        onLongPress = { p -> managing = users.firstOrNull { USER + it.id == p.id } },
        onIntensity = { k ->
            match?.preset?.let { p ->
                vm.clipSettings(clip.copy(adjustments = base.withFilter(p.at(k))))
            }
        }
    )
    managing?.let { up ->
        ManagePresetDialog(
            up,
            onDismiss = { managing = null },
            onRename = {
                UserPresetStore.rename(context, up.id, it)
                managing = null
            },
            onDelete = {
                UserPresetStore.delete(context, up.id)
                managing = null
                vm.message("Preset \"${up.name}\" excluído.")
            }
        )
    }
}

internal const val USER = "user:"
internal const val MINE = "Meus"

/** Stateless content (also used by Paparazzi). Hierarchy: level 4 (escolhas) boxes + slider. */
@Composable
internal fun FiltersContent(
    presets: List<FilterPreset>,
    selectedId: String?,
    intensity: Float,
    thumbnail: (FilterPreset) -> ImageBitmap?,
    onSelect: (FilterPreset) -> Unit,
    onIntensity: (Float) -> Unit,
    onLongPress: (FilterPreset) -> Unit = {},
    initialCategory: String = ALL
) {
    val categories =
        remember(presets) { listOf(ALL) + presets.mapNotNull { it.category }.distinct() }
    var category by remember { mutableStateOf(initialCategory) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (categories.size > 2) {
            ChannelChips(categories, category, label = { it }) { category = it }
        }
        val shown = presets.filter { it.isNone || category == ALL || it.category == category }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { p ->
                FilterBox(
                    p,
                    p.id == selectedId,
                    thumbnail(p),
                    {
                        onLongPress(p)
                    }.takeIf { p.id.startsWith(USER) }
                ) { onSelect(p) }
            }
        }
        val selected = presets.firstOrNull { it.id == selectedId }
        if (selected != null && !selected.isNone && !selected.id.startsWith(USER)) {
            TileSlider(
                "Intensidade · ${selected.name}",
                intensity * 100f,
                0f..100f,
                Modifier.fillMaxWidth(),
                default = 100f,
                format = { "%.0f%%".format(it) }
            ) { onIntensity(it / 100f) }
        } else if (selectedId == null) {
            Text("Cor personalizada (ajustes manuais)", color = RiseMuted, fontSize = 12.sp)
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun FilterBox(
    preset: FilterPreset,
    selected: Boolean,
    image: ImageBitmap?,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit
) {
    val border by animateDpAsState(if (selected) 2.dp else 1.dp, riseSpring(), label = "border")
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, riseSpring(), label = "scale")
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(72.dp).graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
            .semantics {
                contentDescription = "Filtro ${preset.name}"
                this.selected = selected
            }
            .combinedClickable(onLongClick = onLongClick, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(72.dp).clip(shape).background(RiseSurfaceHigh)
                .border(border, if (selected) RiseWhite else RiseOutline, shape),
            contentAlignment = Alignment.Center
        ) {
            if (preset.isNone) {
                image?.let {
                    Image(
                        it,
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f
                    )
                }
                Icon(Icons.Rounded.Block, null, Modifier.size(26.dp), tint = RiseWhite)
            } else if (image != null) {
                Image(it = image)
            }
            if (selected && !preset.isNone) {
                Box(
                    Modifier.align(
                        Alignment.TopEnd
                    ).padding(5.dp).size(18.dp).clip(CircleShape).background(RiseWhite),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Check, null, Modifier.size(13.dp), tint = RiseBlack) }
            }
        }
        Text(
            preset.name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) RiseWhite else RiseMuted
        )
    }
}

@Composable
private fun Image(it: ImageBitmap) = androidx.compose.foundation.Image(
    it,
    null,
    Modifier.fillMaxSize(),
    contentScale = ContentScale.Crop
)

/** Built-in sample (sky / skin / foliage bands) colored with the CPU mirror of the shader. */
internal fun sampleThumbnails(
    presets: List<FilterPreset>,
    base: ColorAdjust = ColorAdjust(),
    lookOf: (FilterPreset) -> ColorAdjust = { base.withFilter(it.adjust) }
): Map<String, ImageBitmap> {
    val n = 64
    val src = FloatArray(n * n * 3)
    for (y in 0 until n) {
        for (x in 0 until n) {
            val v = y / (n - 1f)
            val u = x / (n - 1f)
            val (r, g, b) = when {
                v < 0.45f -> Triple(0.35f + 0.3f * v, 0.55f + 0.3f * v, 0.85f) // sky
                // skin / sand
                v < 0.7f -> Triple(0.85f - 0.2f * u, 0.62f - 0.15f * u, 0.5f - 0.1f * u)
                else -> Triple(0.18f + 0.1f * u, 0.42f - 0.2f * (v - 0.7f), 0.16f) // foliage
            }
            val i = (y * n + x) * 3
            src[i] = r
            src[i + 1] = g
            src[i + 2] = b
        }
    }
    return presets.associate { p ->
        val look = lookOf(p)
        val px = IntArray(n * n) { k ->
            val o = ColorAdjustMath.apply(
                look,
                floatArrayOf(
                    src[k * 3],
                    src[k * 3 + 1],
                    src[
                        k * 3 +
                            2
                    ]
                )
            )
            fun c(f: Float) = (f.coerceIn(0f, 1f) * 255f).toInt()
            (0xFF shl 24) or (c(o[0]) shl 16) or (c(o[1]) shl 8) or c(o[2])
        }
        p.id to Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
