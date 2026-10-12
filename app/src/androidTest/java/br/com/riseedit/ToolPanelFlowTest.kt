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
            "Estabilização Rise" to "Analisar e estabilizar",
            "Cortar" to "Aplicar trim",
            "Áudio" to "Importar música / voz",
            "Overlay" to "Importar vídeo sobreposto",
            "Cor" to "Natural",
            "Movimento" to "Adicionar keyframe no cursor",
            "Formato" to "Original"
        )
        panels.forEach { (tool, content) ->
            ui.click(tool, scroll = true)
            ui.waitFor(content)
            ui.click("Concluir")
        }
    }

    private fun editAndReopen(ui: ToolTestUi, vm: EditorViewModel, projectName: String) {
        // Reopening the editor resets the toolbar scroll without changing the project.
        ui.click("Projetos")
        ui.click(projectName, scroll = true)
        ui.await { ui.onMain { vm.state.value.busy == null } }
        ui.click("Texto", scroll = true)
        ui.click("Adicionar no cursor")
        ui.await { ui.onMain { vm.state.value.project.texts.size == 1 } }
        ui.onMain { vm.undo() }
        assertTrue(ui.onMain { vm.state.value.project.texts.isEmpty() })
        ui.onMain { vm.redo() }
        assertEquals(
            "Sua história começa aqui",
            ui.onMain {
                vm.state.value.project.texts.single().text
            }
        )
        ui.click("Concluir")
        ui.click("Projetos")
        ui.click(projectName, scroll = true)
        ui.await { ui.onMain { vm.state.value.busy == null } }
        assertEquals(
            "Sua história começa aqui",
            ui.onMain {
                vm.state.value.project.texts.single().text
            }
        )
    }
}
