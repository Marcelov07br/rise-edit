package br.com.riseedit

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.riseedit.core.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(projects: List<Project>, error: String?, library: ProjectLibrary, onOpen: (Project)->Unit, onSettings: ()->Unit, hasUpdate: Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Project?>(null) }
    var delete by remember { mutableStateOf<Project?>(null) }
    var name by remember { mutableStateOf("") }
    val filtered = projects.filter { (!favorites || it.favorite) && it.name.contains(query,true) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("RISE EDIT",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black,color=RiseGreen); Text("Dê forma ao seu movimento",style=MaterialTheme.typography.bodyMedium) }
            TextButton(onClick=onSettings) { Text(if(hasUpdate) "Config. • nova" else "Configurações") }
        }
        Card(colors=CardDefaults.cardColors(containerColor=RiseGreen.copy(alpha=0.1f)),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Seu próximo vídeo começa aqui",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("Importe, monte sua história e transforme cenas tremidas em movimentos suaves.")
                Button(onClick={ name="Projeto ${projects.size+1}"; create=true },modifier=Modifier.fillMaxWidth().height(60.dp),shape=RoundedCornerShape(16.dp)) { Text("+  Novo Projeto",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold) }
                Text("✦  Estabilização Rise · seu superpoder",color=RiseGreen,style=MaterialTheme.typography.labelLarge)
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Seus projetos",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            Text("${projects.size} salvos",style=MaterialTheme.typography.labelMedium)
        }
        OutlinedTextField(query,{ query=it },modifier=Modifier.fillMaxWidth(),singleLine=true,label={ Text("Buscar projetos") },shape=RoundedCornerShape(16.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(!favorites,{ favorites=false },label={ Text("Recentes") })
            FilterChip(favorites,{ favorites=true },label={ Text("★ Favoritos") })
        }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("▷",style=MaterialTheme.typography.displayLarge,color=RiseGreen)
                    Text(if(projects.isEmpty()) "Uma tela em branco. Muitas histórias." else "Nenhum projeto encontrado",style=MaterialTheme.typography.titleMedium)
                    Text(if(projects.isEmpty()) "Crie um projeto e escolha seus vídeos." else "Ajuste a busca ou o filtro.",style=MaterialTheme.typography.bodyMedium)
                    if(projects.isEmpty()) OutlinedButton(onClick={ name="Meu primeiro vídeo"; create=true },modifier=Modifier.heightIn(min=48.dp)) { Text("Importar mídia em novo projeto") }
                }
            }
        } else LazyVerticalGrid(GridCells.Adaptive(260.dp),modifier=Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items(filtered,key={ it.id }) { p ->
                Card(shape=RoundedCornerShape(20.dp)) {
                    Column {
                        Box(Modifier.fillMaxWidth().height(145.dp).clickable { onOpen(p) }) {
                            MediaThumbnail(p.clips.firstOrNull()?.uri,p.clips.firstOrNull()?.startMs ?: 0,Modifier.fillMaxSize())
                            Surface(Modifier.align(Alignment.BottomEnd).padding(10.dp),color=RiseBackground.copy(alpha=0.85f),shape=RoundedCornerShape(8.dp)) { Text(time(p.durationMs),Modifier.padding(6.dp),style=MaterialTheme.typography.labelMedium) }
                        }
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(p.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,modifier=Modifier.clickable { onOpen(p) })
                            Text(SimpleDateFormat("dd MMM · HH:mm",Locale("pt","BR")).format(Date(p.modifiedAt)),style=MaterialTheme.typography.labelMedium)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                TextButton(onClick={ library.favorite(p) }) { Text(if(p.favorite) "★" else "☆",color=RiseGreen) }
                                var menu by remember { mutableStateOf(false) }
                                Box { TextButton(onClick={ menu=true }) { Text("•••") }; DropdownMenu(menu,{menu=false}) {
                                    DropdownMenuItem(text={Text("Abrir projeto")},onClick={menu=false;onOpen(p)})
                                    DropdownMenuItem(text={Text("Renomear")},onClick={menu=false;name=p.name;rename=p})
                                    DropdownMenuItem(text={Text("Duplicar")},onClick={menu=false;library.duplicate(p)})
                                    DropdownMenuItem(text={Text("Excluir")},onClick={menu=false;delete=p})
                                } }
                            }
                        }
                    }
                }
            }
        }
    }
    if(create || rename != null) AlertDialog(onDismissRequest={create=false;rename=null},title={Text(if(create) "Novo projeto" else "Renomear projeto")},
        text={OutlinedTextField(name,{name=it.take(80)},label={Text("Nome do projeto")},singleLine=true)},
        confirmButton={TextButton(onClick={ if(create) library.create(name)?.let(onOpen) else rename?.let {library.rename(it,name)};create=false;rename=null }) {Text(if(create) "Criar e importar" else "Salvar")} },
        dismissButton={TextButton(onClick={create=false;rename=null}) {Text("Cancelar")} })
    delete?.let { p -> AlertDialog(onDismissRequest={delete=null},title={Text("Excluir ${p.name}?")},text={Text("O projeto será excluído. Seus vídeos originais continuam no aparelho.")},
        confirmButton={TextButton(onClick={library.delete(p);delete=null}) {Text("Excluir")}},dismissButton={TextButton(onClick={delete=null}) {Text("Cancelar")}}) }
}
