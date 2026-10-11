package br.com.riseedit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.riseedit.core.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ProjectLibrary(application: Application) : AndroidViewModel(application) {
    private val store = ProjectStore(application)
    private val mutable = MutableStateFlow<List<Project>>(emptyList())
    val projects = mutable.asStateFlow()
    private val loadingState = MutableStateFlow(true)
    val loading = loadingState.asStateFlow()
    private val error = MutableStateFlow<String?>(null)
    val message = error.asStateFlow()
    init { refresh() }
    fun refresh() { viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { store.list() } }.onSuccess { mutable.value = it }.onFailure { error.value = "Não foi possível abrir a biblioteca: ${it.localizedMessage}" }; loadingState.value=false } }
    fun create(name: String): Project? = runCatching { Project(name = name.trim().ifBlank { "Novo projeto" }).also(store::save) }.onSuccess { refresh() }.onFailure { error.value = "Não foi possível criar o projeto." }.getOrNull()
    fun rename(p: Project, name: String) = update(p.copy(name = name.trim().ifBlank { p.name }))
    fun favorite(p: Project) = update(p.copy(favorite = !p.favorite))
    fun duplicate(p: Project) = update(p.copy(id = UUID.randomUUID().toString(), name = "${p.name} · cópia"))
    private fun update(p: Project) { runCatching { store.save(p.copy(modifiedAt = System.currentTimeMillis())) }.onSuccess { refresh() }.onFailure { error.value = "Falha ao salvar projeto." } }
    fun delete(p: Project) { runCatching { store.delete(p.id) }.onSuccess { refresh() }.onFailure { error.value = "Falha ao excluir projeto." } }
}
