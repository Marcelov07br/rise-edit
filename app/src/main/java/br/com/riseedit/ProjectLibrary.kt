package br.com.riseedit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.riseedit.core.Project
import br.com.riseedit.core.library.ProjectLibraries
import br.com.riseedit.media.ProxyTranscoder
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProjectLibrary(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val libStore = LibraryStore(application)
    private val libs = MutableStateFlow(ProjectLibraries())
    val libraries = libs.asStateFlow()
    /** 0.23.0: selected library survives app restarts (same SharedPreferences mechanism as the store). */
    private val homePrefs = application.getSharedPreferences("rise_home", android.content.Context.MODE_PRIVATE)
    private val selectedState = MutableStateFlow(homePrefs.getString(KEY_LIBRARY, null) ?: ProjectLibraries.DEFAULT_ID)
    private fun setSelected(id: String) {
        selectedState.value = id
        homePrefs.edit().putString(KEY_LIBRARY, id).apply()
    }
    /** Biblioteca aberta na Home (0.22.1). */
    val selected = selectedState.asStateFlow()
    private val mutable = MutableStateFlow<List<Project>>(emptyList())
    val projects = mutable.asStateFlow()
    private val loadingState = MutableStateFlow(true)
    val loading = loadingState.asStateFlow()
    private val error = MutableStateFlow<String?>(null)
    val message = error.asStateFlow()
    init {
        refresh()
    }
    fun refresh() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val list = store.list()
                    val before = libStore.load()
                    val fixed = before.normalized(list.map { it.id })
                    if (fixed != before) libStore.save(fixed)
                    list to fixed
                }
            }.onSuccess { (list, l) ->
                libs.value = l
                val resolved = l.resolveSelected(selectedState.value)
                if (resolved != selectedState.value) setSelected(resolved)
                mutable.value = list
            }.onFailure {
                error.value = "Não foi possível abrir a biblioteca: ${it.localizedMessage}"
            }
            loadingState.value =
                false
        }
    }

    /** 0.19: "Criar projeto" with format chosen up front. */
    fun create(project: Project): Project? =
        runCatching { project.also(store::save).also { assign(it.id, selectedState.value) } }
            .onSuccess { refresh() }
            .onFailure { error.value = "Não foi possível criar o projeto." }.getOrNull()
    fun create(name: String): Project? = runCatching {
        Project(name = name.trim().ifBlank { "Novo projeto" }).also(store::save)
            .also { assign(it.id, selectedState.value) }
    }.onSuccess { refresh() }.onFailure {
        error.value =
            "Não foi possível criar o projeto."
    }.getOrNull()
    fun rename(p: Project, name: String) = update(p.copy(name = name.trim().ifBlank { p.name }))
    fun favorite(p: Project) = update(p.copy(favorite = !p.favorite))
    fun duplicate(p: Project) {
        val copy = p.copy(id = UUID.randomUUID().toString(), name = "${p.name} · cópia")
        assign(copy.id, libs.value.folderOf(p.id))
        update(copy)
    }

    private fun assign(id: String, folder: String?) {
        libs.value = libs.value.assignNew(id, folder).also(libStore::save)
    }
    private fun editLibs(f: (ProjectLibraries) -> ProjectLibraries) {
        runCatching {
            libs.value = f(libs.value).normalized(mutable.value.map { it.id }).also(libStore::save)
        }.onFailure { error.value = "Falha ao salvar bibliotecas." }
    }
    fun select(folderId: String) = setSelected(folderId)
    fun createLibrary(name: String) {
        val id = UUID.randomUUID().toString()
        editLibs { it.create(name, id) }
        setSelected(id)
    }
    fun renameLibrary(id: String, name: String) = editLibs { it.rename(id, name) }
    fun deleteLibrary(id: String) {
        editLibs { it.delete(id, mutable.value.map { p -> p.id }) }
        if (selectedState.value == id) setSelected(libs.value.resolveSelected(null))
    }
    fun move(p: Project, folderId: String) = editLibs { it.move(p.id, folderId) }
    private fun update(p: Project) {
        runCatching {
            store.save(p.copy(modifiedAt = System.currentTimeMillis()))
        }.onSuccess { refresh() }.onFailure {
            error.value =
                "Falha ao salvar projeto."
        }
    }
    fun delete(p: Project) {
        viewModelScope.launch {
            try {
                val keep = withContext(Dispatchers.IO) {
                    store.delete(p.id)
                    libStore.save(libs.value.forget(p.id).also { libs.value = it })
                    store.list().flatMap {
                        it.clips + it.overlays.map { o -> o.clip }
                    }.mapNotNull { it.proxyUri }.toSet()
                }
                ProxyTranscoder(getApplication()).cleanupUnused(keep)
                refresh()
            } catch (
                e: CancellationException
            ) {
                throw e
            } catch (e: Exception) {
                error.value = "Falha ao excluir projeto."
            }
        }
    }

    private companion object { const val KEY_LIBRARY = "selectedLibrary" }
}
