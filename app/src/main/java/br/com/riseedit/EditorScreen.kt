@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.ui.PlayerView
import br.com.riseedit.core.*

@Composable
fun EditorScreen(vm: EditorViewModel,state: EditorState,onBack: ()->Unit,onSettings: ()->Unit) {
    var tool by remember {mutableStateOf("Corte")}
    var full by remember {mutableStateOf(false)}
    var exportDialog by remember {mutableStateOf(false)}
    var audioTrack by remember {mutableIntStateOf(0)}
    var overlayTrack by remember {mutableIntStateOf(0)}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {vm.import(it)}
    val audio=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {vm.importAudio(it,audioTrack)}
    val overlay=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {vm.importOverlay(it,overlayTrack)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) {it?.let(vm::export)}
    val enabled=state.busy==null
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack,enabled=enabled) {Text("‹ Projetos")}
            Text(state.project.name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold)
            Button(onClick={exportDialog=true},enabled=enabled && state.project.clips.isNotEmpty()) {Text("Exportar")}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(!full) Preview(vm,state,Modifier.fillMaxWidth().height(230.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(time(state.positionMs)+" / "+time(state.project.durationMs),style=MaterialTheme.typography.labelMedium,modifier=Modifier.weight(1f))
                TextButton(onClick=vm::mute) {Text(if(state.muted) "Som off" else "Som on")}
                FilledTonalButton(onClick=vm::togglePlay,enabled=enabled && state.project.clips.isNotEmpty()) {Text(if(state.playing) "Ⅱ" else "▶")}
                TextButton(onClick={full=true}) {Text("⛶")}
            }
            if(state.project.clips.isNotEmpty()) Slider(state.positionMs.toFloat().coerceIn(0f,state.project.durationMs.toFloat()),onValueChange={vm.player.setScrubbingModeEnabled(true);vm.seek(it.toLong())},onValueChangeFinished={vm.player.setScrubbingModeEnabled(false)},valueRange=0f..state.project.durationMs.toFloat(),enabled=enabled)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick={import.launch(arrayOf("video/*"))},enabled=enabled) {Text("+ Vídeo")}
                OutlinedButton(onClick={tool="Áudio";audio.launch(arrayOf("audio/*"))},enabled=enabled && state.project.clips.isNotEmpty()) {Text("+ Áudio")}
                OutlinedButton(onClick={overlay.launch(arrayOf("video/*"))},enabled=enabled && state.project.clips.isNotEmpty()) {Text("+ Overlay")}
                TextButton(onClick=vm::undo,enabled=enabled && state.canUndo) {Text("↶ Desfazer")}
                TextButton(onClick=vm::redo,enabled=enabled && state.canRedo) {Text("↷ Refazer")}
            }
            if(state.project.clips.isEmpty()) Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("Vamos criar seu vídeo",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    Text("Escolha seus vídeos. Depois corte, adicione música e estabilize as cenas.")
                    Button(onClick={import.launch(arrayOf("video/*"))},enabled=enabled,modifier=Modifier.fillMaxWidth().height(56.dp)) {Text("Importar vídeos")}
                }
            } else {
                FilledTonalButton(onClick={tool="Estabilizar"},modifier=Modifier.fillMaxWidth().height(52.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=RiseGreen.copy(alpha=0.16f),contentColor=RiseGreen)) {Text("✦ Estabilização Rise",fontWeight=FontWeight.Bold)}
                TimelinePanel(vm,state)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("Corte","Estabilizar","Texto","Áudio","Overlay","Cor","Movimento","Formato").forEach {name ->FilterChip(tool==name,{tool=name},label={Text(name)},modifier=Modifier.heightIn(min=48.dp))}
                }
                Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {ToolPanel(tool,vm,state,chooseAudioTrack={audioTrack=it},chooseOverlayTrack={overlayTrack=it},addAudio={audio.launch(arrayOf("audio/*"))},addOverlay={overlay.launch(arrayOf("video/*"))})}
                }
            }
            state.message?.let {Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(12.dp)) {Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {Text(it,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall);TextButton(onClick={vm.message(null)}) {Text("OK")}}} }
            TextButton(onClick=onSettings) {Text("Configurações e atualizações")}
            Spacer(Modifier.height(16.dp))
        }
    }
    if(state.busy!=null) Dialog(onDismissRequest={},properties=DialogProperties(dismissOnBackPress=false,dismissOnClickOutside=false)) {
        Card {Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {Text(state.busy,style=MaterialTheme.typography.titleMedium);LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth());Text("${(state.progress*100).toInt()}%");OutlinedButton(onClick=vm::cancel) {Text("Cancelar")}}}
    }
    if(full) Dialog(onDismissRequest={full=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Color.Black) {Column(Modifier.safeDrawingPadding()) {TextButton(onClick={full=false}) {Text("Fechar tela cheia")};Preview(vm,state,Modifier.weight(1f).fillMaxWidth());Button(onClick=vm::togglePlay,modifier=Modifier.align(Alignment.CenterHorizontally)) {Text(if(state.playing) "Pausar" else "Reproduzir")}}}
    }
    if(exportDialog) ExportDialog(state.project.export,onClose={exportDialog=false},onExport={settings ->vm.projectSettings(state.project.aspectRatio,settings);exportDialog=false;export.launch(state.project.name.replace(Regex("[^\\p{L}\\p{N} _-]"),"")+".mp4")})
}

@Composable private fun Preview(vm: EditorViewModel,state: EditorState,modifier: Modifier) {
    Box(modifier.background(Color.Black,RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {
        if(state.project.clips.isEmpty()) Text("PRÉVIA",color=Color.Gray)
        else AndroidView(factory={context ->PlayerView(context).apply {player=vm.player;useController=false;setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)}},modifier=Modifier.fillMaxSize(),onRelease={it.player=null})
        if(state.comparing) Surface(Modifier.align(Alignment.TopStart).padding(8.dp),color=Color.Black.copy(alpha=0.7f),shape=RoundedCornerShape(6.dp)) {Text("Original · sem estabilização",Modifier.padding(6.dp),style=MaterialTheme.typography.labelSmall)}
    }
}

@Composable private fun ExportDialog(initial: ExportSettings,onClose: ()->Unit,onExport: (ExportSettings)->Unit) {
    var value by remember {mutableStateOf(initial)}
    AlertDialog(onDismissRequest=onClose,title={Text("Exportar vídeo")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Resolução");ChoiceRow(listOf(480,720,1080),value.height,{value=value.copy(height=it)}) {"${it}p"}
        Text("Limite de quadros por segundo");ChoiceRow(listOf(24,30,60),value.fps,{value=value.copy(fps=it)}) {"$it fps"}
        Text("Qualidade");ChoiceRow(listOf(4,8,16),value.bitrate/1_000_000,{value=value.copy(bitrate=it*1_000_000)}) {when(it){4->"Leve";8->"Boa";else->"Alta"}}
        Text("MP4 · H.264 / AAC. O vídeo é salvo em um arquivo novo. A taxa de quadros não aumenta acima da fonte.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={Button(onClick={onExport(value)}) {Text("Escolher destino")}},dismissButton={TextButton(onClick=onClose) {Text("Cancelar")}})
}
