package br.com.riseedit

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.riseedit.core.*
import kotlin.math.*

@Composable fun <T> ChoiceRow(choices: List<T>,selected: T,change: (T)->Unit,label: (T)->String) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {choices.forEach {v ->FilterChip(v==selected,{change(v)},label={Text(label(v))})} }
}
@Composable private fun ValueSlider(label: String,value: Float,range: ClosedFloatingPointRange<Float>,enabled: Boolean=true,change: (Float)->Unit) {
    var local by remember(value) {mutableFloatStateOf(value.coerceIn(range))}
    Text("$label · ${"%.2f".format(local)}",style=MaterialTheme.typography.labelLarge)
    Slider(local,{local=it},onValueChangeFinished={change(local)},valueRange=range,enabled=enabled)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ToolPanel(tool: String,vm: EditorViewModel,state: EditorState,chooseAudioTrack: (Int)->Unit,chooseOverlayTrack: (Int)->Unit,addAudio: ()->Unit,addOverlay: ()->Unit) {
    val p=state.project;val clip=p.clips.getOrNull(state.selected) ?: return;val enabled=state.busy==null
    Text(tool,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    when(tool) {
        "Corte" -> {
            Text(clip.name,style=MaterialTheme.typography.bodySmall)
            var trim by remember(clip.id,clip.startMs,clip.endMs) {mutableStateOf(clip.startMs.toFloat()..clip.endMs.toFloat())}
            Text("${time(trim.start.toLong())} → ${time(trim.endInclusive.toLong())}")
            RangeSlider(trim,{if(it.endInclusive-it.start>=40) trim=it},valueRange=0f..clip.sourceDurationMs.toFloat(),enabled=enabled)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={vm.trim(trim.start.toLong(),trim.endInclusive.toLong())},enabled=enabled) {Text("Aplicar trim")}
                OutlinedButton(onClick=vm::split,enabled=enabled) {Text("Dividir no cursor")}
                TextButton(onClick=vm::remove,enabled=enabled) {Text("Excluir clipe")}
            }
            Row {TextButton(onClick={vm.move(-1)},enabled=enabled && state.selected>0) {Text("← Mover")};TextButton(onClick={vm.move(1)},enabled=enabled && state.selected<p.clips.lastIndex) {Text("Mover →")}}
            Text("A faixa principal fecha os espaços automaticamente ao cortar ou excluir.",style=MaterialTheme.typography.bodySmall)
            val current=clip.speedAt(clip.startMs)
            ChoiceRow(listOf(0.5,0.75,1.0,1.5,2.0,4.0),current,{vm.clipSettings(clip.copy(speedPoints=listOf(SpeedPoint(clip.startMs,it))))}) {"${it}×"}
            OutlinedButton(onClick={val rates=listOf(1.0,0.75,0.5,0.75,1.0,1.5,2.0,1.0);vm.clipSettings(clip.copy(speedPoints=rates.mapIndexed {i,v->SpeedPoint(clip.startMs+clip.sourceLengthMs*i/8,v)}))},enabled=enabled) {Text("Rampa de velocidade · pulso")}
            ChoiceRow(listOf(0L,250L,500L),clip.fadeMs,{vm.clipSettings(clip.copy(fadeMs=it))}) {if(it==0L) "Corte seco" else "Fade ${it}ms"}
        }
        "Estabilizar" -> {
            Text("✦ De tremido a suave",color=RiseGreen,fontWeight=FontWeight.Bold)
            Text("1. Escolha um modo  2. Analise  3. Compare e ajuste",style=MaterialTheme.typography.bodySmall)
            val analyzed=clip.uri in state.analyzed;val s=clip.stabilization
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("Suave","Forte","Câmera fixa").forEach {mode ->OutlinedButton(onClick={vm.settings(when(mode) {"Suave"->Stabilization(analyzed,MotionGoal.SMOOTH,0.65,0.4,1.10);"Forte"->Stabilization(analyzed,MotionGoal.SMOOTH,1.0,1.1,1.25);else->Stabilization(analyzed,MotionGoal.FIXED,1.0,0.8,1.25)})},enabled=enabled) {Text(mode)}}
            }
            Button(onClick=vm::analyze,enabled=enabled,modifier=Modifier.fillMaxWidth().height(52.dp)) {Text(if(analyzed) "Analisar novamente" else "Analisar e estabilizar")}
            if(analyzed) {
                Row {FilterChip(s.enabled,{vm.settings(s.copy(enabled=!s.enabled))},label={Text(if(s.enabled) "Ativada" else "Desativada")});TextButton(onClick=vm::compare) {Text(if(state.comparing) "Ver estabilizado" else "Antes / depois")}}
                ValueSlider("Intensidade",s.strength.toFloat(),0f..1f,enabled) {vm.settings(s.copy(strength=it.toDouble()))}
                ValueSlider("Suavidade (s)",s.smoothSeconds.toFloat(),0.1f..2f,enabled) {vm.settings(s.copy(smoothSeconds=it.toDouble().coerceIn(0.1,2.0)))}
                ValueSlider("Crop automático · limite de zoom",s.maxZoom.toFloat(),1f..1.25f,enabled) {vm.settings(s.copy(maxZoom=it.toDouble().coerceIn(1.0,1.25)))}
            }
            Text("Até 2 minutos por fonte. Confira o resultado: cenas com pouca textura ou objetos grandes em movimento podem exigir ajustes.",style=MaterialTheme.typography.bodySmall)
        }
        "Texto" -> {
            val layer=p.texts.firstOrNull {it.id==state.selection}
            var draft by remember(layer?.id) {mutableStateOf(layer?.text ?: "Sua história começa aqui")}
            OutlinedTextField(draft,{draft=it.take(160)},label={Text("Texto ou legenda")},modifier=Modifier.fillMaxWidth(),maxLines=4)
            Button(onClick={val start=state.positionMs.coerceIn(0,maxOf(0,p.durationMs-1));vm.text(layer?.copy(text=draft) ?: TextLayer(text=draft,startMs=start,endMs=min(p.durationMs,start+3000)))},enabled=enabled && draft.isNotBlank()) {Text(if(layer==null) "Adicionar no cursor" else "Atualizar texto")}
            if(layer!=null) {
                ValueSlider("Início (s)",layer.startMs/1000f,0f..max(0.001f,(p.durationMs-1)/1000f)) {val start=(it*1000).toLong();vm.text(layer.copy(startMs=start,endMs=max(start+1,layer.endMs)))}
                ValueSlider("Duração (s)",(layer.endMs-layer.startMs)/1000f,0.001f..max(0.001f,(p.durationMs-layer.startMs)/1000f)) {vm.text(layer.copy(endMs=layer.startMs+(it*1000).toLong().coerceAtLeast(1)))}
                ValueSlider("Tamanho",layer.size,0.5f..2f) {vm.text(layer.copy(size=it))}
                ValueSlider("Posição horizontal",layer.x,-0.8f..0.8f) {vm.text(layer.copy(x=it))}
                ValueSlider("Posição vertical",layer.y,-0.8f..0.8f) {vm.text(layer.copy(y=it))}
                ChoiceRow(listOf(-1,0xFFBEFF68.toInt(),0xFFFFC66D.toInt()),layer.color,{vm.text(layer.copy(color=it))}) {when(it){-1->"Branco";0xFFBEFF68.toInt()->"Verde";else->"Dourado"}}
                FilterChip(layer.animated,{vm.text(layer.copy(animated=!layer.animated))},label={Text("Entrada e saída suaves")})
                TextButton(onClick=vm::removeLayer) {Text("Excluir texto")}
            } else Text("Selecione uma legenda na timeline para editar duração e aparência.",style=MaterialTheme.typography.bodySmall)
        }
        "Áudio" -> {
            val a=p.audio.firstOrNull {it.id==state.selection}
            var track by remember {mutableIntStateOf(0)}
            ChoiceRow(listOf(0,1,2),track,{track=it;chooseAudioTrack(it)}) {"Faixa ${it+1}"}
            Button(onClick=addAudio,enabled=enabled) {Text("Importar música / voz")}
            if(a!=null) {
                Text(a.name)
                ValueSlider("Volume",a.volume,0f..2f) {vm.audio(a.copy(volume=it))}
                ValueSlider("Fade de entrada e saída (s)",a.fadeMs/1000f,0f..3f) {vm.audio(a.copy(fadeMs=(it*1000).toLong()))}
                var trim by remember(a.id,a.startMs,a.endMs) {mutableStateOf(a.startMs.toFloat()..a.endMs.toFloat())}
                RangeSlider(trim,{if(it.endInclusive-it.start>=40) trim=it},valueRange=0f..a.sourceDurationMs.toFloat())
                Button(onClick={vm.audio(a.copy(startMs=trim.start.toLong(),endMs=trim.endInclusive.toLong()))}) {Text("Aplicar corte de áudio")}
                TextButton(onClick=vm::removeLayer) {Text("Excluir áudio")}
            } else {Text("Som original do clipe selecionado");ValueSlider("Volume",clip.volume,0f..2f) {vm.clipSettings(clip.copy(volume=it))}}
        }
        "Overlay" -> {
            val o=p.overlays.firstOrNull {it.id==state.selection};var track by remember {mutableIntStateOf(0)}
            ChoiceRow(listOf(0,1),track,{track=it;chooseOverlayTrack(it)}) {"Camada ${it+1}"}
            Button(onClick=addOverlay,enabled=enabled) {Text("Importar vídeo sobreposto")}
            if(o!=null) {
                ValueSlider("Escala",o.scale,0.1f..1f) {vm.overlay(o.copy(scale=it))}
                ValueSlider("Posição horizontal",o.x,-0.8f..0.8f) {vm.overlay(o.copy(x=it))}
                ValueSlider("Posição vertical",o.y,-0.8f..0.8f) {vm.overlay(o.copy(y=it))}
                ValueSlider("Opacidade",o.opacity,0f..1f) {vm.overlay(o.copy(opacity=it))}
                var trim by remember(o.id,o.clip.startMs,o.clip.endMs) {mutableStateOf(o.clip.startMs.toFloat()..o.clip.endMs.toFloat())}
                RangeSlider(trim,{if(it.endInclusive-it.start>=40) trim=it},valueRange=0f..o.clip.sourceDurationMs.toFloat())
                Button(onClick={vm.overlay(o.copy(clip=o.clip.trim(trim.start.toLong(),trim.endInclusive.toLong())))}) {Text("Aplicar corte")}
                TextButton(onClick=vm::removeLayer) {Text("Excluir overlay")}
            } else Text("Importe um vídeo e selecione seu bloco para ajustar o picture-in-picture.",style=MaterialTheme.typography.bodySmall)
        }
        "Cor" -> {
            val a=clip.adjustments
            ChoiceRow(listOf("Natural","Cinema","Vivo","P&B"),"",{vm.clipSettings(clip.copy(adjustments=when(it){"Cinema"->Adjustments(-0.03f,0.15f,-0.15f,0.25f);"Vivo"->Adjustments(0.03f,0.1f,0.25f,0.1f);"P&B"->Adjustments(saturation=-1f);else->Adjustments()}))}) {it}
            ValueSlider("Brilho",a.brightness,-0.5f..0.5f) {vm.clipSettings(clip.copy(adjustments=a.copy(brightness=it)))}
            ValueSlider("Contraste",a.contrast,-0.8f..0.8f) {vm.clipSettings(clip.copy(adjustments=a.copy(contrast=it)))}
            ValueSlider("Saturação",a.saturation,-1f..1f) {vm.clipSettings(clip.copy(adjustments=a.copy(saturation=it)))}
            ValueSlider("Temperatura",a.warmth,-1f..1f) {vm.clipSettings(clip.copy(adjustments=a.copy(warmth=it)))}
        }
        "Movimento" -> {
            val offset=p.clips.take(state.selected).sumOf {it.durationMs};val source=clip.sourceAt(state.positionMs-offset)
            val key=br.com.riseedit.core.Timeline.keyframe(clip,source)
            fun save(k: TransformKeyframe) {vm.clipSettings(clip.copy(keyframes=(clip.keyframes.filter {it.sourceMs!=source}+k.copy(sourceMs=source)).sortedBy {it.sourceMs}))}
            Text("${clip.keyframes.size} keyframes · cursor ${time(state.positionMs)}",style=MaterialTheme.typography.bodySmall)
            Button(onClick={save(key)}) {Text("◆ Adicionar keyframe no cursor")}
            ValueSlider("Posição X",key.x,-1f..1f) {save(key.copy(x=it))}
            ValueSlider("Posição Y",key.y,-1f..1f) {save(key.copy(y=it))}
            ValueSlider("Escala",key.scale,0.5f..2f) {save(key.copy(scale=it))}
            ValueSlider("Rotação",key.rotation,-180f..180f) {save(key.copy(rotation=it))}
            ValueSlider("Opacidade",key.opacity,0f..1f) {save(key.copy(opacity=it))}
            TextButton(onClick={vm.clipSettings(clip.copy(keyframes=emptyList()))}) {Text("Limpar keyframes")}
        }
        "Formato" -> {
            ChoiceRow(listOf(0f,9f/16f,16f/9f,1f),p.aspectRatio,{vm.projectSettings(it,p.export)}) {when(it){0f->"Original";1f->"1:1";9f/16f->"9:16";else->"16:9"}}
            Text("O enquadramento preenche o formato escolhido. Ajuste posição e escala em Movimento.",style=MaterialTheme.typography.bodySmall)
        }
    }
}
