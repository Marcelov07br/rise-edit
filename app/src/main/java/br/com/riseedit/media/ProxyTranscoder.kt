@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package br.com.riseedit.media

import android.content.Context
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.*
import br.com.riseedit.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class ProxyTranscoder(context: Context, cacheLimitBytes: Long = 512L * 1024 * 1024) {
    private val context = context.applicationContext
    private val cache = ProxyCache(this.context, cacheLimitBytes)

    suspend fun prepare(uri: Uri, info: SourceInfo, target: ProxySpec, onProgress: (Float, String) -> Unit = { _, _ -> }): Uri = serial.withLock {
        // A single shared lock also deduplicates concurrent requests across importer instances.
        val file = withContext(Dispatchers.IO) { cache.file(uri, target) }
        if (file.exists() && file.length() > 0) {
            withContext(Dispatchers.IO) { file.setLastModified(System.currentTimeMillis()) }
            return@withLock Uri.fromFile(file)
        }
        onProgress(0f, "Preparando vídeo compatível…")
        val probe = MediaProbe.probe(context, uri)
        if (!probe.decoderFound) throw if (info.mime == "video/apv") MediaFault.UnsupportedApv else MediaFault.MissingDecoder(info.mime)
        if (info.audioMime != null && !probe.audioDecoderFound) throw MediaFault.MissingDecoder(info.audioMime)
        val hevc = target.mime == MimeTypes.VIDEO_H265 &&
            DeviceCapability.canEncode(MimeTypes.VIDEO_H265, target.width, target.height, target.fps, target.bitrate)
        if (!hevc && !DeviceCapability.canEncodeAvc(target.width, target.height, target.fps, target.bitrate)) {
            throw MediaFault.ProxyFailed("Este aparelho não oferece um codificador H.264.")
        }
        if (info.audioMime != null && !DeviceCapability.canEncodeAac()) throw MediaFault.ProxyFailed("Este aparelho não oferece um codificador AAC compatível.")
        val partial = File(file.parentFile, "${file.nameWithoutExtension}.partial.mp4")
        try {
            withContext(Dispatchers.IO) { partial.delete() }
            convert(uri, info, target, partial, onProgress, hevc)
            onProgress(1f, "Finalizando vídeo compatível…")
            withContext(Dispatchers.IO) {
                check(partial.length() > 0 && partial.renameTo(file)) { "Não foi possível guardar o vídeo convertido." }
            }
            Uri.fromFile(file)
        } catch (e: CancellationException) { throw e }
        catch (e: MediaFault) { throw e }
        catch (e: Exception) { throw MediaFault.ProxyFailed(cause = e) }
        finally { withContext(NonCancellable + Dispatchers.IO) { partial.delete() } }
    }

    private suspend fun convert(uri: Uri, info: SourceInfo, target: ProxySpec, output: File, progress: (Float, String) -> Unit, hevc: Boolean = false) = withContext(Dispatchers.Main.immediate) {
        val completion = CompletableDeferred<Unit>()
        val encoder = DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(
            VideoEncoderSettings.Builder().setBitrate(target.bitrate)
                .apply {
                    // HEVC Main = 8-bit 4:2:0 (0.24.0 proxies); H.264 Baseline as before.
                    if (hevc) setEncodingProfileLevel(CodecProfileLevel.HEVCProfileMain, CodecProfileLevel.HEVCMainTierLevel41)
                    else setEncodingProfileLevel(CodecProfileLevel.AVCProfileBaseline, CodecProfileLevel.AVCLevel41)
                }.build()
        ).build()
        val engine = Transformer.Builder(context).setEncoderFactory(encoder)
            .apply { FrameCompat.factory()?.let { setVideoFrameProcessorFactory(it) } }
            .setVideoMimeType(if (hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { completion.complete(Unit) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { completion.completeExceptionally(exportException) }
            }).build()
        val audio = if (info.audioMime == null) emptyList() else listOf(
            ChannelMixingAudioProcessor().apply { (1..6).forEach { putChannelMixingMatrix(ChannelMixingMatrix.createForConstantPower(it, 2)) } },
            SonicAudioProcessor().apply { setOutputSampleRateHz(48000) }
        )
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(Effects(audio, listOf(
            Presentation.createForWidthAndHeight(target.width, target.height, Presentation.LAYOUT_SCALE_TO_FIT),
            FrameDropEffect.createDefaultFrameDropEffect(target.fps.toFloat())
        ))).build()
        val composition = Composition.Builder(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(item)))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
        try {
            engine.start(composition, output.absolutePath)
            val holder = ProgressHolder()
            while (!completion.isCompleted) {
                if (engine.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress / 100f, "Convertendo vídeo…")
                delay(150)
            }
            completion.await()
        } finally { engine.cancel() }
    }

    /** Bytes in the proxy cache (Configurações › Proxies). */
    suspend fun cacheBytes() = withContext(Dispatchers.IO) { cache.bytes() }

    suspend fun cleanupUnused(keep: Set<String>) = serial.withLock { withContext(Dispatchers.IO) { cache.deleteUnused(keep) } }
    suspend fun trimCache(keep: Set<String>) = serial.withLock { withContext(Dispatchers.IO) { cache.trim(keep) } }
    companion object { private val serial = Mutex() }
}
