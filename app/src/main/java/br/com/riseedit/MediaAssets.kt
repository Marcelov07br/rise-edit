package br.com.riseedit

import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.nio.ByteOrder
import kotlin.math.*

object MediaAssets {
    private val thumbnails = LruCache<String,Bitmap>(48)
    private val waves = LruCache<String,List<Float>>(32)
    private val workers = Semaphore(2)
    suspend fun thumbnail(context: Context, uri: String, time: Long): Bitmap? = withContext(Dispatchers.IO) { workers.withPermit {
        val key = "$uri:$time"; thumbnails.get(key)?.let { return@withPermit it }
        runCatching { val r = MediaMetadataRetriever(); try { r.setDataSource(context,Uri.parse(uri)); r.getScaledFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,256,144)?.also { thumbnails.put(key,it) } } finally { r.release() } }.getOrNull()
    } }
    /** Sparse RMS samples decoded from actual PCM, across the entire source (not decorative bars). */
    suspend fun waveform(context: Context, uri: String, duration: Long): List<Float>? = withContext(Dispatchers.IO) { workers.withPermit {
        waves.get(uri)?.let { return@withPermit it }
        val extractor = MediaExtractor(); var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context,Uri.parse(uri),null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return@withPermit null
            extractor.selectTrack(track); val format=extractor.getTrackFormat(track)
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format,null,null,0); codec.start()
            val info=MediaCodec.BufferInfo(); val bins=mutableListOf<Float>()
            repeat(64) { bin ->
                currentCoroutineContext().ensureActive()
                extractor.seekTo((duration*1000*bin/64),MediaExtractor.SEEK_TO_CLOSEST_SYNC); codec.flush()
                var sum=0.0; var count=0; var ended=false; var attempts=0
                while(count < 2048 && !ended && attempts++ < 20) {
                    val input=codec.dequeueInputBuffer(5000)
                    if(input>=0) { val buffer=codec.getInputBuffer(input)!!; val n=extractor.readSampleData(buffer,0)
                        codec.queueInputBuffer(input,0,max(0,n),max(0,extractor.sampleTime),if(n<0) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0); if(n>=0) extractor.advance()
                    }
                    val output=codec.dequeueOutputBuffer(info,5000)
                    if(output>=0) {
                        val buffer=codec.getOutputBuffer(output)!!; buffer.position(info.offset);buffer.limit(info.offset+info.size);buffer.order(ByteOrder.nativeOrder())
                        val encoding=codec.outputFormat.let { if(it.containsKey(MediaFormat.KEY_PCM_ENCODING)) it.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT }
                        val size=if(encoding==AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        while(buffer.remaining()>=size && count<2048) { val sample=if(size==4) buffer.float.toDouble() else buffer.short/32768.0;sum+=sample*sample;count++ }
                        ended=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0;codec.releaseOutputBuffer(output,false)
                    }
                }
                bins.add(if(count==0) 0f else sqrt(sum/count).toFloat())
            }
            val peak=bins.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
            bins.map { (it/peak).coerceIn(0f,1f) }.also { waves.put(uri,it) }
        } catch(e: CancellationException) { throw e } catch(e: Exception) { null }
        finally { runCatching { codec?.stop() };codec?.release();extractor.release() }
    } }
}

@Composable
fun MediaThumbnail(uri: String?, sourceMs: Long, modifier: Modifier = Modifier) {
    val context=LocalContext.current
    var image by remember(uri,sourceMs) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri,sourceMs) { image=uri?.let { MediaAssets.thumbnail(context,it,sourceMs) } }
    Box(modifier.background(RiseBackground),contentAlignment=Alignment.Center) {
        image?.let { Image(it.asImageBitmap(),contentDescription=null,modifier=Modifier.matchParentSize(),contentScale=ContentScale.Crop) } ?: Text("▷")
    }
}
