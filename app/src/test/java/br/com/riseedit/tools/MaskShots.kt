package br.com.riseedit.tools

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import br.com.riseedit.BottomToolbar
import br.com.riseedit.RiseBlack
import br.com.riseedit.RiseTheme
import br.com.riseedit.RiseWhite
import br.com.riseedit.ToolbarItem
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.ColorAdjustMath
import br.com.riseedit.core.mask.Mask
import br.com.riseedit.core.mask.MaskGeom
import br.com.riseedit.core.mask.MaskKey
import br.com.riseedit.core.mask.MaskMath
import br.com.riseedit.core.mask.MaskOp
import br.com.riseedit.core.mask.MaskSet
import br.com.riseedit.core.mask.MaskType
import org.junit.Rule
import org.junit.Test

@Composable
private fun Screen(title: String, content: @Composable () -> Unit) = RiseTheme {
    Column(Modifier.fillMaxSize().background(RiseBlack).padding(16.dp)) {
        Text(
            title,
            color = RiseWhite,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        content()
    }
}

private val face = Mask(
    id = "face",
    type = MaskType.ELLIPSE,
    geom = MaskGeom(0.42f, 0.45f, 0.28f, 0.6f, -12f, 0.12f),
    keys = listOf(
        MaskKey(0, MaskGeom(0.42f, 0.45f, 0.28f, 0.6f, -12f, 0.12f)),
        MaskKey(1500, MaskGeom(0.55f, 0.45f, 0.28f, 0.6f, -5f, 0.12f))
    )
)
private val sky =
    Mask(
        id = "sky",
        type = MaskType.LINEAR,
        geom = MaskGeom(0.5f, 0.22f, 1f, 0.18f, 0f, 0.05f),
        op = MaskOp.ADD
    )
private val hole =
    Mask(
        id = "hole",
        type = MaskType.RECT,
        geom = MaskGeom(0.8f, 0.7f, 0.18f, 0.25f, 8f, 0.04f),
        roundness = 0.4f,
        op = MaskOp.SUBTRACT
    )
private val skin = Mask(id = "skin", type = MaskType.COLOR, op = MaskOp.INTERSECT, opacity = 0.8f)
private val combined = MaskSet(listOf(face, sky, hole, skin))

private const val W = 320
private const val H = 180

/** Procedural frame (sky / skin-ish subject / foliage) so the masked grade is visible. */
private fun frame(u: Float, v: Float): FloatArray {
    val dx = (u - 0.45f) * 16f / 9f
    val dy = v - 0.48f
    val subject = dx * dx / 0.04f + dy * dy / 0.12f < 1f
    return when {
        subject -> floatArrayOf(0.78f - 0.1f * v, 0.56f - 0.05f * v, 0.45f)
        v < 0.45f -> floatArrayOf(0.35f + 0.3f * v, 0.55f + 0.3f * v, 0.85f)
        else -> floatArrayOf(0.18f + 0.1f * u, 0.42f - 0.2f * (v - 0.45f), 0.16f)
    }
}

/** CPU mirror of the shader: graded = adjust(frame); out = mix(frame, graded, coverage). */
private fun render(masks: List<Mask>, view: Boolean): Bitmap {
    val look = ColorAdjust(exposure = 0.6f, warmth = 0.4f, saturation = 0.25f)
    val px = IntArray(W * H)
    for (y in 0 until H) {
        for (x in 0 until W) {
            val u = (x + 0.5f) / W
            val v = (y + 0.5f) / H
            val src = frame(u, v)
            val k = MaskMath.coverage(masks, u, v, W.toFloat() / H, src)
            val c = if (view) {
                floatArrayOf(k, k, k)
            } else {
                val o = ColorAdjustMath.apply(look, src)
                FloatArray(3) { src[it] + (o[it] - src[it]) * k }
            }
            fun b(f: Float) = (f.coerceIn(0f, 1f) * 255).toInt()
            px[y * W + x] = (0xFF shl 24) or (b(c[0]) shl 16) or (b(c[1]) shl 8) or b(c[2])
        }
    }
    return Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888)
}

@Composable
private fun PreviewFrame(masks: List<Mask>, view: Boolean, handles: Boolean, selected: String?) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))) {
        Image(
            render(masks, view).asImageBitmap(),
            null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
        )
        if (handles) MaskHandles(masks, selected, {}, { _, _ -> })
    }
}

class MaskShots {
    @get:Rule val p =
        Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    @Test fun maskTool() = p.snapshot {
        Screen("Clipe › Máscara") {
            BottomToolbar(
                listOf(
                    ToolbarItem("Dividir", Icons.Outlined.VerticalSplit) {},
                    ToolbarItem("Velocidade", Icons.Outlined.Speed) {},
                    ToolbarItem("Cortar", Icons.Outlined.ContentCut) {},
                    ToolbarItem("Cor", Icons.Outlined.Palette) {},
                    ToolbarItem("Máscara", Icons.Outlined.FilterCenterFocus, active = true) {},
                    ToolbarItem("Animação", Icons.Outlined.AutoAwesome) {}
                )
            )
            Box(Modifier.padding(top = 16.dp)) {
                MaskContent(
                    MaskOwner.CLIP,
                    "praia.mp4",
                    MaskSet(listOf(face)),
                    "face",
                    700,
                    false,
                    MaskUi()
                )
            }
        }
    }

    @Test fun maskEmpty() = p.snapshot {
        Screen("Camada de ajuste › Máscara") {
            MaskContent(MaskOwner.LAYER, "Ajuste 1", MaskSet(), null, 0, false, MaskUi())
        }
    }

    @Test fun handlesOnPreview() = p.snapshot {
        Screen("Alças na prévia (elipse selecionada)") {
            val masks = combined.at(700)
            PreviewFrame(masks, view = false, handles = true, selected = "face")
            Box(Modifier.padding(top = 16.dp)) {
                PreviewFrame(
                    listOf(sky.copy(geom = sky.geom.copy(rotation = 15f))),
                    view = false,
                    handles = true,
                    selected = "sky"
                )
            }
        }
    }

    @Test fun combinedList() = p.snapshot {
        Screen("Máscaras combinadas") {
            MaskContent(
                MaskOwner.LAYER,
                "Ajuste 1",
                combined,
                "hole",
                700,
                false,
                MaskUi(onPaste = {
                })
            )
        }
    }

    @Test fun colorMask() = p.snapshot {
        Screen("Máscara por cor") {
            MaskContent(
                MaskOwner.CLIP,
                "praia.mp4",
                combined,
                "skin",
                700,
                false,
                MaskUi(onEyedropper = {
                }, picking = true)
            )
        }
    }

    @Test fun maskView() = p.snapshot {
        Screen("Ver máscara (cobertura em cinza)") {
            val masks = combined.at(700)
            PreviewFrame(masks, view = true, handles = false, selected = null)
            Box(Modifier.padding(top = 16.dp)) {
                PreviewFrame(masks, view = false, handles = false, selected = null)
            }
            Text(
                "Em cima: cobertura combinada. Embaixo: ajuste aplicado só dentro.",
                color = RiseWhite,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }

    @Test fun overlayTarget() = p.snapshot {
        Screen("Overlay › Máscara") {
            MaskContent(
                MaskOwner.OVERLAY,
                "logo.mp4",
                MaskSet(listOf(hole.copy(op = MaskOp.ADD))),
                "hole",
                0,
                false,
                MaskUi()
            )
        }
    }
}
