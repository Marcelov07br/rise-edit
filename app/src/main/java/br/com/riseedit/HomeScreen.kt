package br.com.riseedit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.riseedit.core.Project
import br.com.riseedit.core.library.LibraryFolder
import br.com.riseedit.core.library.ProjectLibraries
import br.com.riseedit.designsystem.AdaptiveTokens
import br.com.riseedit.designsystem.PanelIconButton
import br.com.riseedit.designsystem.PillButton
import br.com.riseedit.designsystem.ScreenWidth
import br.com.riseedit.designsystem.riseSafeArea
import br.com.riseedit.designsystem.screenWidth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home actions; defaults are no-ops so screenshots can render without a ViewModel. */
data class HomeActions(
    val open: (Project) -> Unit = {},
    val settings: () -> Unit = {},
    val new: () -> Unit = {},
    val favorite: (Project) -> Unit = {},
    val rename: (Project, String) -> Unit = { _, _ -> },
    val duplicate: (Project) -> Unit = {},
    val delete: (Project) -> Unit = {},
    val move: (Project, String) -> Unit = { _, _ -> },
    val select: (String) -> Unit = {},
    val createLibrary: (String) -> Unit = {},
    val renameLibrary: (String, String) -> Unit = { _, _ -> },
    val deleteLibrary: (String) -> Unit = {}
)

fun ProjectLibrary.homeActions(open: (Project) -> Unit, settings: () -> Unit, new: () -> Unit) =
    HomeActions(
        open, settings, new, ::favorite, ::rename, ::duplicate, ::delete, ::move, ::select,
        ::createLibrary, ::renameLibrary, ::deleteLibrary
    )

private sealed interface HomeDialog {
    data class RenameProject(val p: Project) : HomeDialog
    data class DeleteProject(val p: Project) : HomeDialog
    data object NewLibrary : HomeDialog
    data class RenameLibrary(val f: LibraryFolder) : HomeDialog
    data class DeleteLibrary(val f: LibraryFolder, val count: Int) : HomeDialog
}

@Composable
fun HomeScreen(
    projects: List<Project>,
    error: String?,
    libraries: ProjectLibraries,
    selected: String,
    actions: HomeActions
) {
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<HomeDialog?>(null) }
    val ids = projects.map { it.id }
    val folders = libraries.folders.ifEmpty { listOf(LibraryFolder(ProjectLibraries.DEFAULT_ID, ProjectLibraries.DEFAULT_NAME)) }
    val current = folders.firstOrNull { it.id == selected } ?: folders.first()
    val inFolder = projects.filter { libraries.folderOf(it.id) == current.id }
    val filtered = ProjectLibraries.filter(
        projects, libraries, current.id, query, favorites, { it.id }, { it.name }, { it.favorite }
    )
    val wide = screenWidth() != ScreenWidth.COMPACT
    val libMenu: @Composable (LibraryFolder) -> Unit = { f ->
        LibraryMenu(f, onRename = { dialog = HomeDialog.RenameLibrary(f) }, onDelete = {
            val n = libraries.count(f.id, ids)
            if (n == 0) actions.deleteLibrary(f.id) else dialog = HomeDialog.DeleteLibrary(f, n)
        })
    }
    val grid: @Composable (Modifier) -> Unit = { m ->
        LazyVerticalGrid(
            if (wide) GridCells.Adaptive(200.dp) else GridCells.Fixed(2),
            modifier = m,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = if (wide) 24.dp else 16.dp, vertical = 12.dp)
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!wide) {
                        HomeTitle(actions.settings)
                        Hero(compact = true, onNew = actions.new)
                        LibraryChips(folders, current.id, libraries, ids, actions.select, { dialog = HomeDialog.NewLibrary }, libMenu)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(current.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RiseWhite)
                                Text("${inFolder.size} projetos", style = MaterialTheme.typography.labelMedium, color = RiseMuted)
                            }
                            PillButton("+  Novo projeto", onClick = actions.new)
                            Spacer(Modifier.width(8.dp))
                            PanelIconButton(Icons.Rounded.Settings, "Configurações", onClick = actions.settings)
                        }
                    }
                    SearchRow(query, { query = it }, favorites, { favorites = it }, wide)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(inFolder.isEmpty(), actions.new)
                }
            } else {
                items(filtered, key = { it.id }) { p ->
                    ProjectCard(p, folders, libraries.folderOf(p.id), actions, onRename = {
                        dialog = HomeDialog.RenameProject(p)
                    }, onDelete = { dialog = HomeDialog.DeleteProject(p) })
                }
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize().riseSafeArea()) {
            LibrarySide(folders, current.id, libraries, ids, actions, { dialog = HomeDialog.NewLibrary }, libMenu)
            grid(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        grid(Modifier.fillMaxSize().riseSafeArea())
    }
    HomeDialogs(dialog, actions) { dialog = null }
}

@Composable
private fun HomeTitle(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("RISE EDIT", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = RiseWhite)
            Text("Dê forma ao seu movimento", style = MaterialTheme.typography.bodySmall)
        }
        PanelIconButton(Icons.Rounded.Settings, "Configurações", onClick = onSettings)
    }
}

/** Compact hero: one line of copy + bounded pill (no full-width giant button). */
@Composable
private fun Hero(compact: Boolean, onNew: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(RiseWhite.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("Seu próximo vídeo começa aqui", fontWeight = FontWeight.Bold, color = RiseWhite, fontSize = if (compact) 15.sp else 17.sp)
            Text("✦ Estabilização Rise · seu superpoder", style = MaterialTheme.typography.labelMedium, color = RiseMuted)
        }
        PillButton("+ Novo", Modifier.widthIn(max = 140.dp), onClick = onNew)
    }
}

@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit, fav: Boolean, onFav: (Boolean) -> Unit, wide: Boolean) {
    val field = @Composable { m: Modifier ->
        OutlinedTextField(query, onQuery, modifier = m, singleLine = true, label = { Text("Buscar nesta biblioteca") }, shape = RoundedCornerShape(16.dp))
    }
    val chips = @Composable {
        FilterChip(!fav, { onFav(false) }, label = { Text("Recentes") })
        FilterChip(fav, { onFav(true) }, label = { Text("★ Favoritos") })
    }
    if (wide) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            field(Modifier.widthIn(max = 420.dp).weight(1f, fill = false))
            chips()
        }
    } else {
        field(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun LibraryChips(
    folders: List<LibraryFolder>,
    selected: String,
    libs: ProjectLibraries,
    ids: List<String>,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    menu: @Composable (LibraryFolder) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folders, key = { it.id }) { f ->
                FilterChip(f.id == selected, { onSelect(f.id) }, label = {
                    Text("${f.name} · ${libs.count(f.id, ids)}", maxLines = 1)
                })
            }
            item(key = "+") {
                AssistChip(onClick = onNew, label = { Text("Biblioteca") }, leadingIcon = { Icon(Icons.Rounded.Add, null) })
            }
        }
        folders.firstOrNull { it.id == selected }?.let { menu(it) }
    }
}

@Composable
private fun LibrarySide(
    folders: List<LibraryFolder>,
    selected: String,
    libs: ProjectLibraries,
    ids: List<String>,
    actions: HomeActions,
    onNew: () -> Unit,
    menu: @Composable (LibraryFolder) -> Unit
) {
    Column(
        Modifier.width(AdaptiveTokens.SideColumn).fillMaxHeight().background(RiseSurface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("RISE EDIT", fontWeight = FontWeight.Black, color = RiseWhite, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
        Text("Dê forma ao seu movimento", style = MaterialTheme.typography.labelSmall, color = RiseMuted, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp))
        Text("BIBLIOTECAS", style = MaterialTheme.typography.labelSmall, color = RiseMuted, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(folders, key = { it.id }) { f ->
                val on = f.id == selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (on) RiseWhite.copy(alpha = 0.12f) else RiseBlack.copy(alpha = 0f))
                        .clickable { actions.select(f.id) }.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(f.name, Modifier.weight(1f), color = if (on) RiseWhite else RiseMuted, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${libs.count(f.id, ids)}", color = RiseMuted, fontSize = 12.sp)
                    menu(f)
                }
            }
        }
        TextButton(onClick = onNew) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Nova biblioteca")
        }
    }
}

@Composable
private fun LibraryMenu(f: LibraryFolder, onRename: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "Opções da biblioteca ${f.name}", tint = RiseMuted) }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("Renomear biblioteca") }, onClick = { open = false; onRename() })
            if (f.id != ProjectLibraries.DEFAULT_ID) {
                DropdownMenuItem(text = { Text("Excluir biblioteca") }, onClick = { open = false; onDelete() })
            }
        }
    }
}

@Composable
private fun ProjectCard(
    p: Project,
    folders: List<LibraryFolder>,
    folderId: String,
    actions: HomeActions,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clickable { actions.open(p) }) {
                MediaThumbnail(p.clips.firstOrNull()?.effectiveUri(true), p.clips.firstOrNull()?.startMs ?: 0, Modifier.fillMaxSize())
                Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp), color = RiseBackground.copy(alpha = 0.85f), shape = RoundedCornerShape(6.dp)) {
                    Text(time(p.durationMs), Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(Modifier.padding(start = 10.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { actions.open(p) }) {
                    Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = RiseWhite)
                    Text(SimpleDateFormat("dd MMM · HH:mm", Locale("pt", "BR")).format(Date(p.modifiedAt)), style = MaterialTheme.typography.labelSmall, color = RiseMuted)
                }
                Text(
                    if (p.favorite) "★" else "☆",
                    Modifier.clip(RoundedCornerShape(50)).clickable { actions.favorite(p) }.padding(6.dp),
                    color = RiseWhite
                )
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, Modifier.size(36.dp)) { Icon(Icons.Rounded.MoreVert, "Opções de ${p.name}", tint = RiseMuted) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Abrir projeto") }, onClick = { menu = false; actions.open(p) })
                        DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; actions.duplicate(p) })
                        folders.filter { it.id != folderId }.forEach { f ->
                            DropdownMenuItem(text = { Text("Mover para ${f.name}") }, onClick = { menu = false; actions.move(p, f.id) })
                        }
                        DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(emptyFolder: Boolean, onNew: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 220.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("▷", style = MaterialTheme.typography.displayMedium, color = RiseWhite)
            Text(if (emptyFolder) "Uma tela em branco. Muitas histórias." else "Nenhum projeto encontrado", style = MaterialTheme.typography.titleMedium)
            Text(if (emptyFolder) "Crie um projeto nesta biblioteca e escolha seus vídeos." else "Ajuste a busca ou o filtro.", style = MaterialTheme.typography.bodyMedium)
            if (emptyFolder) PillButton("Importar mídia em novo projeto", onClick = onNew)
        }
    }
}

/** Shared name dialog (Nova biblioteca / Renomear biblioteca / Renomear projeto). */
@Composable
fun NameDialog(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        NameDialogBody(title, label, initial, confirm, onDismiss) { onConfirm(it); onDismiss() }
    }
}

/** Dialog card itself (separate so Paparazzi can render it without a window). */
@Composable
fun NameDialogBody(title: String, label: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    Surface(Modifier.widthIn(min = 280.dp, max = 420.dp), shape = RoundedCornerShape(24.dp), color = RiseSurface) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = RiseWhite)
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) }
            }
        }
    }
}

@Composable
private fun HomeDialogs(d: HomeDialog?, a: HomeActions, close: () -> Unit) {
    when (d) {
        null -> Unit
        is HomeDialog.RenameProject -> NameDialog("Renomear projeto", "Nome do projeto", d.p.name, "Salvar", close) { a.rename(d.p, it) }
        is HomeDialog.NewLibrary -> NameDialog("Nova biblioteca", "Nome da biblioteca", "", "Criar", close) { a.createLibrary(it) }
        is HomeDialog.RenameLibrary -> NameDialog("Renomear biblioteca", "Nome da biblioteca", d.f.name, "Salvar", close) { a.renameLibrary(d.f.id, it) }
        is HomeDialog.DeleteLibrary -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Excluir ${d.f.name}?") },
            text = { Text("${d.count} projeto(s) serão movidos para ${ProjectLibraries.DEFAULT_NAME}. Nenhum projeto é apagado.") },
            confirmButton = { TextButton(onClick = { a.deleteLibrary(d.f.id); close() }) { Text("Excluir e mover") } },
            dismissButton = { TextButton(onClick = close) { Text("Cancelar") } }
        )
        is HomeDialog.DeleteProject -> AlertDialog(
            onDismissRequest = close,
            title = { Text("Excluir ${d.p.name}?") },
            text = { Text("O projeto será excluído. Seus vídeos originais continuam no aparelho.") },
            confirmButton = { TextButton(onClick = { a.delete(d.p); close() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = close) { Text("Cancelar") } }
        )
    }
}
