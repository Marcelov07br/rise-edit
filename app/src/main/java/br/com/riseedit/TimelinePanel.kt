@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class, androidx.media3.common.util.ExperimentalApi::class])
package br.com.riseedit

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.riseedit.core.*
import kotlin.math.*

@Composable
fun TimelinePanel(vm: EditorViewModel,state: EditorState,trackAreaHeight: Dp=270.dp) {
    var zoom by remember { mutableFloatStateOf(55f) }
    var snap by remember { mutableStateOf(true) }
    val p=state.project; val density=LocalDensity.current.density
    val width=max(320f,p.durationMs/1000f*zoom+80f)
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("TIMELINE",style=MaterialTheme.typography.labelLarge,modifier=Modifier.weight(1f))
            FilterChip(snap,{snap=!snap},label={Text("Ímã")})
            TextButton(onClick={zoom=(zoom/1.4f).coerceAtLeast(15f)}) {Text("−")}
            TextButton(onClick={zoom=(zoom*1.4f).coerceAtMost(180f)}) {Text("+")}
        }
        val vertical=rememberScrollState()
        Row(Modifier.fillMaxWidth().height(trackAreaHeight)) {
            Column(Modifier.width(62.dp).verticalScroll(vertical)) {
                Text("",Modifier.height(28.dp)); listOf("Vídeo","Overlay 1","Overlay 2","Texto","Áudio 1","Áudio 2","Áudio 3").forEach { Text(it,Modifier.height(52.dp).padding(top=14.dp),style=MaterialTheme.typography.labelSmall) }
            }
            Column(Modifier.weight(1f).verticalScroll(vertical).horizontalScroll(rememberScrollState())) {
                Box(Modifier.width(width.dp)) {
                    Column {
                        Canvas(Modifier.fillMaxWidth().height(28.dp).pointerInput(p.durationMs,zoom) {
                            detectTapGestures { vm.seek((it.x/density/zoom*1000).toLong()) }
                        }.pointerInput(p.durationMs,zoom) {
                            detectHorizontalDragGestures(onDragStart={vm.player.setScrubbingModeEnabled(true)},onDragEnd={vm.player.setScrubbingModeEnabled(false)},onDragCancel={vm.player.setScrubbingModeEnabled(false)}) { change,_ ->change.consume();vm.seek((change.position.x/density/zoom*1000).toLong()) }
                        }.pointerInput(Unit) { detectTransformGestures { _,_,scale,_ -> if(abs(scale-1)>0.005f) zoom=(zoom*scale).coerceIn(15f,180f) } }) {
                            val step=if(zoom>80) 1 else if(zoom>30) 2 else 5
                            val paint=android.graphics.Paint().apply {color=android.graphics.Color.LTGRAY;textSize=9*density;isAntiAlias=true}
                            for(second in 0..ceil(p.durationMs/1000.0).toInt() step step) {val x=second*zoom*density;drawLine(Color.Gray,Offset(x,16*density),Offset(x,25*density),1f);drawContext.canvas.nativeCanvas.drawText("${second}s",x+3*density,12*density,paint)}
                        }
                        TrackRow(width) {
                            var offset=0L
                            p.clips.forEachIndexed { i,c ->
                                val start=offset;offset+=c.durationMs
                                TimelineItem(c.id,c.name,start,c.durationMs,zoom,state.selection==null && i==state.selected,RiseGreen,enabled=state.busy==null,
                                    onSelect={vm.select(i)},onMove={ms -> vm.select(i); val target=(p.clips.runningFold(0L) { t,clip -> t+clip.durationMs }.indexOfLast { it<=ms }).coerceIn(0,p.clips.lastIndex);vm.move(target-i) }) {
                                    Row(Modifier.fillMaxSize()) {repeat(3) { frame ->MediaThumbnail(c.uri,c.sourceAt(c.durationMs*frame/3),Modifier.weight(1f).fillMaxHeight())} }
                                }
                            }
                        }
                        repeat(2) { track -> TrackRow(width) { p.overlays.filter {it.track==track}.forEach { o -> TimelineItem(o.id,o.clip.name,o.offsetMs,o.clip.durationMs,zoom,state.selection==o.id,Color(0xFFB7ABFF),state.busy==null,{vm.selectLayer(o.id)},{vm.moveLayer(o.id,it,snap)}) {MediaThumbnail(o.clip.uri,o.clip.startMs,Modifier.fillMaxSize())} } } }
                        TrackRow(width) {p.texts.forEach { t ->TimelineItem(t.id,t.text,t.startMs,t.endMs-t.startMs,zoom,state.selection==t.id,Color(0xFFFFC66D),state.busy==null,{vm.selectLayer(t.id)},{vm.moveLayer(t.id,it,snap)})} }
                        repeat(3) { track -> TrackRow(width) {p.audio.filter {it.track==track}.forEach { a ->TimelineItem(a.id,a.name,a.offsetMs,a.durationMs,zoom,state.selection==a.id,Color(0xFF70CFFF),state.busy==null,{vm.selectLayer(a.id)},{vm.moveLayer(a.id,it,snap)}) {Waveform(a)} }} }
                    }
                    Canvas(Modifier.matchParentSize()) {val x=state.positionMs/1000f*zoom*density;drawLine(Color.White,Offset(x,0f),Offset(x,size.height),2*density);drawCircle(RiseGreen,5*density,Offset(x,5*density))}
                }
            }
        }
        Text("Toque para selecionar · segure e arraste para mover",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun TrackRow(width: Float,content: @Composable BoxScope.()->Unit) {Box(Modifier.width(width.dp).height(52.dp).padding(vertical=3.dp).background(RiseSurface.copy(alpha=0.7f),RoundedCornerShape(6.dp)),content=content)}
@Composable private fun BoxScope.TimelineItem(id: String,name: String,start: Long,duration: Long,zoom: Float,selected: Boolean,color: Color,enabled: Boolean,onSelect: ()->Unit,onMove: (Long)->Unit,background: (@Composable ()->Unit)?=null) {
    val density=LocalDensity.current.density;var drag by remember(id,start) {mutableFloatStateOf(0f)}
    Box(Modifier.offset(x=(start/1000f*zoom+drag/density).dp).width(max(8f,duration/1000f*zoom).dp).fillMaxHeight()
        .clip(RoundedCornerShape(6.dp)).background(color.copy(alpha=0.3f)).border(if(selected) 2.dp else 1.dp,if(selected) Color.White else color,RoundedCornerShape(6.dp))
        .clickable(enabled=enabled,onClick=onSelect).pointerInput(id,start,zoom,enabled) {
            if(enabled) detectDragGesturesAfterLongPress(onDragStart={onSelect()},onDragEnd={onMove((start+drag/density/zoom*1000).toLong().coerceAtLeast(0));drag=0f},onDragCancel={drag=0f}) {change,amount ->change.consume();drag+=amount.x}
        }) {
        background?.invoke()
        Text(name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=Color.White,modifier=Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha=0.6f)).padding(3.dp))
    }
}
@Composable private fun Waveform(a: AudioClip) {
    val context=LocalContext.current
    var samples by remember(a.uri) {mutableStateOf<List<Float>?>(null)}
    LaunchedEffect(a.uri) {samples=MediaAssets.waveform(context,a.uri,a.sourceDurationMs)}
    Canvas(Modifier.fillMaxSize().padding(vertical=7.dp)) {samples?.let { data ->
        data.forEachIndexed { i,v ->val source=a.sourceDurationMs*i.toFloat()/data.size; if(source>=a.startMs && source<=a.endMs) {val x=(source-a.startMs)/a.durationMs*size.width;val h=max(1f,v*size.height*0.45f);drawLine(Color(0xFF70CFFF),Offset(x,size.height/2-h),Offset(x,size.height/2+h),2f)} }
    }}
}
