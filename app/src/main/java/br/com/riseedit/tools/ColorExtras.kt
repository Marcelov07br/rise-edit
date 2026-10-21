package br.com.riseedit.tools

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.EditorViewModel
import br.com.riseedit.GradeJson
import br.com.riseedit.RiseMuted
import br.com.riseedit.color.GradeLuts
import br.com.riseedit.core.Clip
import br.com.riseedit.core.ColorAdjust
import br.com.riseedit.core.color.ColorGrade
import br.com.riseedit.core.color.ColorKeyGroup
import br.com.riseedit.core.color.CubeExport
import br.com.riseedit.core.color.UserPreset
import br.com.riseedit.core.editing.AttributeCopy
import br.com.riseedit.core.editing.AttributeGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** App-wide user presets ("Meus" in Filtros): filesDir/user_presets.json (versioned list). */
internal object UserPresetStore {
    val presets = mutableStateListOf<UserPreset>()
    private var loaded = false

    private fun file(c: Context) = File(c.filesDir, "user_presets.json")

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val f = file(c)
            if (!f.exists()) return
            val o = JSONObject(f.readText())
            val arr = o.optJSONArray("presets") ?: JSONArray()
            presets.clear()
            for (i in 0 until arr.length()) UserPreset.fromMap(GradeJson.plain(arr.getJSONObject(i)))?.let(presets::add)
        }.onFailure { Log.w("RiseColor", "user presets unreadable", it) }
    }

    private fun save(c: Context) {
        val arr = JSONArray()
        presets.forEach { arr.put(JSONObject(UserPreset.toMap(it))) }
        val tmp = File(c.filesDir, "user_presets.json.tmp")
        tmp.writeText(JSONObject().put("v", UserPreset.VERSION).put("presets", arr).toString())
        tmp.renameTo(file(c))
    }

    fun add(c: Context, name: String, a: ColorAdjust, groups: Set<ColorKeyGroup>) {
        load(c)
        presets += UserPreset(UUID.randomUUID().toString(), name.trim(), a, groups).let { it.copy(adjust = it.owned()) }
        save(c)
    }

    fun rename(c: Context, id: String, name: String) {
        val i = presets.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        presets[i] = presets[i].copy(name = name.trim())
        save(c)
    }

    fun delete(c: Context, id: String) {
        presets.removeAll { it.id == id }
        save(c)
    }
}

internal val PRESET_ATTRIBUTES = listOf(
    AttributeGroup.COLOR_BASIC, AttributeGroup.COLOR_CURVES, AttributeGroup.COLOR_HSL, AttributeGroup.COLOR_WHEELS
)

internal fun AttributeGroup.keyGroup() = when (this) {
    AttributeGroup.COLOR_CURVES -> ColorKeyGroup.CURVES
    AttributeGroup.COLOR_HSL -> ColorKeyGroup.HSL
    AttributeGroup.COLOR_WHEELS -> ColorKeyGroup.WHEELS
    else -> ColorKeyGroup.BASIC
}

/** Name + group checklist (same AttributeCopy selection logic as Copiar/Colar). Stateless. */
@Composable
internal fun SavePresetDialog(
    a: ColorAdjust,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (String, Set<ColorKeyGroup>) -> Unit
) {
    val shown = remember(a) {
        PRESET_ATTRIBUTES.filter {
            when (it) {
                AttributeGroup.COLOR_BASIC -> !a.basic.isNeutral
                AttributeGroup.COLOR_CURVES -> !a.curves.isIdentity
                AttributeGroup.COLOR_HSL -> !a.hsl.isNeutral
                else -> !a.wheels.isNeutral
            }
        }.ifEmpty { listOf(AttributeGroup.COLOR_BASIC) }
    }
    var sel by remember(shown) { mutableStateOf(AttributeCopy.all(shown)) }
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Salvar preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it.take(32) }, label = { Text("Nome") }, singleLine = true)
                Text("Incluir", color = RiseMuted, fontSize = 12.sp)
                shown.forEach { g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(g in sel, { sel = AttributeCopy.toggle(sel, g, it, shown) })
                        Text(g.label)
                    }
                }
                Text("Aparece em Filtros › Meus. LUT/log não entram no preset.", color = RiseMuted, fontSize = 11.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, sel.map { it.keyGroup() }.toSet()) }, enabled = name.isNotBlank() && sel.isNotEmpty()) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Long-press on a "Meus" preset: rename or delete. */
@Composable
internal fun ManagePresetDialog(preset: UserPreset, onDismiss: () -> Unit, onRename: (String) -> Unit, onDelete: () -> Unit) {
    var name by remember(preset.id) { mutableStateOf(preset.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Preset") },
        text = { OutlinedTextField(name, { name = it.take(32) }, label = { Text("Nome") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Renomear") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Excluir", color = androidx.compose.ui.graphics.Color(0xFFFF8A80)) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

/** CPU mirror of the shader for one rgb (same order as ColorPass: LUT before/after adjust). */
internal fun gradeTransform(adjust: ColorAdjust, grade: ColorGrade): (FloatArray) -> FloatArray {
    val lut = GradeLuts.lut(grade.lutUri)
    fun g(c: FloatArray): FloatArray =
        if (grade.isIdentity) c else grade.apply(doubleArrayOf(c[0].toDouble(), c[1].toDouble(), c[2].toDouble()), lut).let { o -> FloatArray(3) { o[it].toFloat() } }
    return { c ->
        var x = if (grade.lutBeforeAdjust) g(c) else c
        if (!adjust.isNeutral) x = br.com.riseedit.core.ColorAdjustMath.apply(adjust, x)
        if (grade.lutBeforeAdjust) x else g(x)
    }
}

/** Size choice (33/65) + keyframe warning, then SAF CREATE_DOCUMENT and write the .cube. */
@Composable
internal fun ExportCubeDialog(vm: EditorViewModel, clip: Clip, adjust: ColorAdjust, grade: ColorGrade, onDismiss: () -> Unit) {
    var size by remember { mutableStateOf(33) }
    val scope = rememberCoroutineScope()
    val title = clip.name.substringBeforeLast('.').ifBlank { "Rise Edit" }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        onDismiss()
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val t0 = System.nanoTime()
            val ok = withContext(Dispatchers.Default) {
                runCatching {
                    val text = CubeExport.write(title, size, gradeTransform(adjust, grade))
                    vm.appContext.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }.onFailure { Log.w("RiseColor", "cube export failed", it) }.isSuccess
            }
            Log.i("RiseColor", "cube export ${size}³ ok=$ok in ${(System.nanoTime() - t0) / 1_000_000}ms")
            vm.message(if (ok) "LUT .cube ${size}³ exportado." else "Não foi possível salvar o .cube.")
        }
    }
    ExportCubeContent(size, { size = it }, !clip.colorKeys.isEmpty, onDismiss) { create.launch(CubeExport.fileName(title)) }
}

@Composable
internal fun ExportCubeContent(size: Int, onSize: (Int) -> Unit, hasKeys: Boolean, onDismiss: () -> Unit, onExport: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Exportar LUT .cube") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                br.com.riseedit.designsystem.RiseDropdown("Tamanho", CubeExport.SIZES, size, text = { "$it³" + if (it == 65) " (mais preciso, ~9 MB)" else " (padrão)" }) { onSize(it) }
                if (hasKeys) Text("Este clipe tem keyframes de cor: o .cube usa só o valor no cursor (LUT não anima).", color = androidx.compose.ui.graphics.Color(0xFFFFC94D), fontSize = 12.sp)
                Text("Ajustes + LUT/log do clipe no tempo atual, mesma matemática da prévia/exportação. Funciona no Resolve, Premiere, CapCut.", color = RiseMuted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onExport) { Text("Salvar…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
