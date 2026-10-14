package br.com.riseedit

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import br.com.riseedit.core.Clip
import br.com.riseedit.core.Project
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises typed tool routing and a real edit/history/save cycle through the editor. */
class ToolPanelFlowTest {
    @Test
    fun toolsOpenAndTextEditSurvivesUndoRedoAndReopen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File.createTempFile("tool-flow-", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("compatibility/h264_720p_scrub.mp4").use { input ->
            source.outputStream().use { input.copyTo(it) }
        }
        val clip =
            Clip(
                uri = Uri.fromFile(source).toString(),
                name = "Cena de teste",
                sourceDurationMs = 3000
            )
        val project =
            Project(listOf(clip), name = "Ferramentas ${UUID.randomUUID().toString().take(8)}")
        val store = ProjectStore(context)
        val ui = ToolTestUi(instrumentation)
        var activity: MainActivity? = null
        try {
            store.save(project)
            activity = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ) as MainActivity
            val host = activity
            val vm = ui.onMain { ViewModelProvider(host).get(EditorViewModel::class.java) }
            ui.click(project.name, scroll = true)
            ui.await {
                ui.onMain {
                    vm.state.value.busy == null &&
                        vm.state.value.project.id == project.id
                }
            }
            checkPanels(ui)
            editAndReopen(ui, vm, project.name)
        } finally {
            activity?.let { host -> ui.onMain { host.finish() } }
            store.delete(project.id)
            source.delete()
        }
    }

    private fun checkPanels(ui: ToolTestUi) {
        val panels = listOf(
            "Estabilizar" to "Analisar e estabilizar",
            "Cortar" to "Aplicar trim",
            "Formato" to "Original"
        )
        panels.forEachIndexed { index, (tool, content) ->
            ui.click(tool, scroll = true)
            ui.waitFor(content)
            ui.screenshot("tool-$index")
            ui.click("Concluir")
        }
        // Sub-toolbars replace the bottom bar; "Voltar" pops one level.
        ui.click("Áudio", scroll = true)
        listOf("Música", "Efeitos sonoros", "Gravar", "Extrair").forEach { ui.waitFor(it) }
        ui.screenshot("toolbar-audio")
        ui.click("Voltar")
        ui.click("Overlay", scroll = true)
        ui.waitFor("Adicionar overlay")
        // Overlay tools require a typed Overlay selection; the submenu only adds.
        assertTrue(listOf("Opacidade", "Máscara", "Mistura").none { ui.isVisible(it) })
        ui.click("Voltar")
        ui.click("Cor", scroll = true)
        ui.click("Filtros")
        ui.waitFor("Cinema")
        ui.screenshot("toolbar-color")
        ui.click("Concluir")
        ui.click("Voltar")
        assertTrue(listOf("Movimento", "Posição X", "Escala").none { ui.isVisible(it) })
    }

    private fun editAndReopen(ui: ToolTestUi, vm: EditorViewModel, projectName: String) {
        // Reopening the editor resets the toolbar scroll without changing the project.
        ui.click("Projetos")
        ui.click(projectName, scroll = true)
        ui.await { ui.onMain { vm.state.value.busy == null } }
        ui.click("Texto", scroll = true)
        ui.waitFor("Adicionar texto")
        ui.screenshot("toolbar-text")
        ui.click("Adicionar texto")
        ui.waitFor("Editor de texto")
        ui.setText("Texto", "Sua história começa aqui")
        ui.click("Estilos")
        ui.click("Contorno")
        ui.waitFor("Plano de fundo")
        ui.screenshot("text-editor")
        ui.click("Fontes")
        ui.click("Bebas Neue")
        ui.screenshot("text-editor-fonts")
        ui.click("Confirmar texto")
        ui.await(description = {
            ui.screenshot("text-failure")
            ui.onMain {
                val state = vm.state.value
                "Text not committed: busy=${state.busy} texts=${state.project.texts}"
            }
        }) { ui.onMain { vm.state.value.project.texts.size == 1 } }
        val saved = ui.onMain { vm.state.value.project.texts.single() }
        assertEquals("Sua história começa aqui", saved.text)
        assertTrue(saved.style.strokeWidth > 0f)
        assertEquals("bebas", saved.style.font)
        ui.waitFor("Editar")
        ui.screenshot("toolbar-text-selected")
        ui.onMain { vm.undo() }
        assertTrue(ui.onMain { vm.state.value.project.texts.isEmpty() })
        ui.onMain { vm.redo() }
        ui.click("Projetos")
        ui.click(projectName, scroll = true)
        ui.await { ui.onMain { vm.state.value.busy == null } }
        assertEquals(saved, ui.onMain { vm.state.value.project.texts.single() })
    }
}
