package br.com.riseedit.media

import android.content.Context
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import br.com.riseedit.core.ChromaFormat
import br.com.riseedit.core.ColorDepth
import br.com.riseedit.core.MediaFault
import br.com.riseedit.core.SourceInfo
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ProbeResult(
    val info: SourceInfo? = null,
    val decoderFound: Boolean = false,
    val fault: MediaFault? = null,
    val durationMs: Long = 0,
    val decoderName: String? = null,
    val audioMime: String? = null,
    val audioDecoderFound: Boolean = false,
    val audioDecoderName: String? = null
) {
    val sourceInfo: SourceInfo? get() = info
    val hasAudio: Boolean get() = audioMime != null
}

/** Reads the selected document locally. Codec discovery is advisory until actual decoding starts. */
object MediaProbe {
    suspend fun probe(context: Context, uri: Uri): ProbeResult = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        try {
            currentCoroutineContext().ensureActive()
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            var video: MediaFormat? = null
            var audio: MediaFormat? = null
            var durationUs = 0L
            for (index in 0 until extractor.trackCount) {
                currentCoroutineContext().ensureActive()
                val format = extractor.getTrackFormat(index)
                val mime = format.stringOrNull(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    if (video == null) video = format
                    durationUs = maxOf(durationUs, format.longOrNull(MediaFormat.KEY_DURATION) ?: 0L)
                } else if (mime.startsWith("audio/")) {
                    if (audio == null) audio = format
                    durationUs = maxOf(durationUs, format.longOrNull(MediaFormat.KEY_DURATION) ?: 0L)
                }
            }
            val audioMime = audio?.stringOrNull(MediaFormat.KEY_MIME)
            val audioDecoder = audio?.let { DeviceCapability.findDecoderForFormat(it) }
            val source = video?.let { sourceInfo(it, audio) }
            val decoder = video?.let { DeviceCapability.findDecoderForFormat(it) }
            currentCoroutineContext().ensureActive()
            val durationMs = if (durationUs > 0) (durationUs / 1_000).coerceAtLeast(1)
                else fallbackDuration(context, uri)
            if (video == null && audio == null) {
                ProbeResult(fault = MediaFault.ImportFailed("O arquivo não contém uma faixa de vídeo ou áudio reconhecida."))
            } else {
                ProbeResult(
                    info = source,
                    decoderFound = decoder != null,
                    durationMs = durationMs,
                    decoderName = decoder,
                    audioMime = audioMime,
                    audioDecoderFound = audioDecoder != null,
                    audioDecoderName = audioDecoder
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: SecurityException) {
            ProbeResult(fault = MediaFault.AssetUnavailable(uri.toString(), error))
        } catch (error: FileNotFoundException) {
            ProbeResult(fault = MediaFault.AssetUnavailable(uri.toString(), error))
        } catch (error: Exception) {
            ProbeResult(fault = MediaFault.ImportFailed("Não foi possível ler as informações deste arquivo.", error))
        } finally {
            // Releasing a failed native extractor must not hide the original failure or cancellation.
            runCatching { extractor?.release() }
        }
    }

    private fun sourceInfo(format: MediaFormat, audio: MediaFormat?): SourceInfo {
        val mime = format.stringOrNull(MediaFormat.KEY_MIME).orEmpty()
        val parsed = CodecSpecificData.parse(format, mime)
        val profile = format.intOrNull(MediaFormat.KEY_PROFILE)?.takeIf { it > 0 } ?: parsed?.profile ?: 0
        val level = format.intOrNull(MediaFormat.KEY_LEVEL)?.takeIf { it > 0 } ?: parsed?.level ?: 0
        // Supply parsed source values to the format-specific codec query when the extractor omitted them.
        if (profile > 0 && format.intOrNull(MediaFormat.KEY_PROFILE) == null) format.setInteger(MediaFormat.KEY_PROFILE, profile)
        if (level > 0 && format.intOrNull(MediaFormat.KEY_LEVEL) == null) format.setInteger(MediaFormat.KEY_LEVEL, level)
        val color = colorFormatInfo(format.intOrNull(MediaFormat.KEY_COLOR_FORMAT))
        val simpleProfile = exactProfileInfo(mime, profile)
        val transfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
        val rotation = (format.intOrNull(MediaFormat.KEY_ROTATION) ?: 0).let { ((it % 360) + 360) % 360 }
        return SourceInfo(
            mime = mime,
            width = (format.intOrNull(MediaFormat.KEY_WIDTH) ?: 0).coerceAtLeast(0),
            height = (format.intOrNull(MediaFormat.KEY_HEIGHT) ?: 0).coerceAtLeast(0),
            profile = profile,
            level = level,
            bitDepth = parsed?.depth ?: color.depth.takeUnless { it == ColorDepth.UNKNOWN } ?: simpleProfile.depth,
            chroma = parsed?.chroma ?: color.chroma.takeUnless { it == ChromaFormat.UNKNOWN } ?: simpleProfile.chroma,
            isHdr = transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG
                || parsed?.transfer == MediaFormat.COLOR_TRANSFER_ST2084 || parsed?.transfer == MediaFormat.COLOR_TRANSFER_HLG
                || isHdrProfile(mime, profile),
            frameRate = format.numberOrNull(MediaFormat.KEY_FRAME_RATE)?.takeIf { it.isFinite() && it > 0 } ?: 0f,
            audioMime = audio?.stringOrNull(MediaFormat.KEY_MIME),
            audioChannels = (audio?.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 0).coerceAtLeast(0),
            audioSampleRate = (audio?.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 0).coerceAtLeast(0),
            rotationDegrees = rotation.takeIf { it % 90 == 0 } ?: 0
        )
    }

    private fun fallbackDuration(context: Context, uri: Uri): Long {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            0
        } finally {
            runCatching { retriever?.release() }
        }
    }

    private fun exactProfileInfo(mime: String, profile: Int): VideoDetails = when (mime) {
        MediaFormat.MIMETYPE_VIDEO_AVC -> when (profile) {
            CodecProfileLevel.AVCProfileBaseline, CodecProfileLevel.AVCProfileConstrainedBaseline,
            CodecProfileLevel.AVCProfileMain, CodecProfileLevel.AVCProfileExtended,
            CodecProfileLevel.AVCProfileHigh, CodecProfileLevel.AVCProfileConstrainedHigh ->
                VideoDetails(ColorDepth.EIGHT, ChromaFormat.YUV420)
            // High 10 / 4:2:2 / 4:4:4 describe permitted tools, not the stream's exact sample layout.
            else -> VideoDetails()
        }
        MediaFormat.MIMETYPE_VIDEO_HEVC -> when {
            profile == CodecProfileLevel.HEVCProfileMain -> VideoDetails(ColorDepth.EIGHT, ChromaFormat.YUV420)
            profile == CodecProfileLevel.HEVCProfileMain10 || profile == CodecProfileLevel.HEVCProfileMain10HDR10 ->
                VideoDetails(chroma = ChromaFormat.YUV420)
            Build.VERSION.SDK_INT >= 29 && profile == CodecProfileLevel.HEVCProfileMain10HDR10Plus ->
                VideoDetails(chroma = ChromaFormat.YUV420)
            else -> VideoDetails()
        }
        MediaFormat.MIMETYPE_VIDEO_VP9 -> when (profile) {
            CodecProfileLevel.VP9Profile0 -> VideoDetails(ColorDepth.EIGHT, ChromaFormat.YUV420)
            CodecProfileLevel.VP9Profile1 -> VideoDetails(depth = ColorDepth.EIGHT)
            CodecProfileLevel.VP9Profile2, CodecProfileLevel.VP9Profile2HDR -> VideoDetails(chroma = ChromaFormat.YUV420)
            else -> VideoDetails()
        }
        "video/av01" -> if (Build.VERSION.SDK_INT >= 29) when (profile) {
            CodecProfileLevel.AV1ProfileMain8 -> VideoDetails(ColorDepth.EIGHT, ChromaFormat.YUV420)
            CodecProfileLevel.AV1ProfileMain10, CodecProfileLevel.AV1ProfileMain10HDR10,
            CodecProfileLevel.AV1ProfileMain10HDR10Plus -> VideoDetails(ColorDepth.TEN, ChromaFormat.YUV420)
            else -> VideoDetails()
        } else VideoDetails()
        // APV is intentionally unknown here: its MIME does not identify its bit depth or chroma profile.
        else -> VideoDetails()
    }

    private fun isHdrProfile(mime: String, profile: Int): Boolean = when (mime) {
        MediaFormat.MIMETYPE_VIDEO_HEVC -> profile == CodecProfileLevel.HEVCProfileMain10HDR10 ||
            (Build.VERSION.SDK_INT >= 29 && profile == CodecProfileLevel.HEVCProfileMain10HDR10Plus)
        MediaFormat.MIMETYPE_VIDEO_VP9 -> profile == CodecProfileLevel.VP9Profile2HDR || profile == CodecProfileLevel.VP9Profile3HDR ||
            (Build.VERSION.SDK_INT >= 29 && (profile == CodecProfileLevel.VP9Profile2HDR10Plus || profile == CodecProfileLevel.VP9Profile3HDR10Plus))
        "video/av01" -> Build.VERSION.SDK_INT >= 29 &&
            (profile == CodecProfileLevel.AV1ProfileMain10HDR10 || profile == CodecProfileLevel.AV1ProfileMain10HDR10Plus)
        MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> true
        else -> false
    }

    @Suppress("DEPRECATION")
    private fun colorFormatInfo(color: Int?): VideoDetails {
        // MediaCodecInfo's P010 constant is public from API 33; ImageFormat's P010 is a different API.
        if (Build.VERSION.SDK_INT >= 33 && color == CodecCapabilities.COLOR_FormatYUVP010) {
            return VideoDetails(ColorDepth.TEN, ChromaFormat.YUV420)
        }
        return when (color) {
            CodecCapabilities.COLOR_FormatYUV420Flexible, CodecCapabilities.COLOR_FormatYUV420Planar,
            CodecCapabilities.COLOR_FormatYUV420PackedPlanar, CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
            CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar -> VideoDetails(ColorDepth.EIGHT, ChromaFormat.YUV420)
            CodecCapabilities.COLOR_FormatYUV422Flexible, CodecCapabilities.COLOR_FormatYUV422Planar,
            CodecCapabilities.COLOR_FormatYUV422PackedPlanar, CodecCapabilities.COLOR_FormatYUV422SemiPlanar,
            CodecCapabilities.COLOR_FormatYUV422PackedSemiPlanar, CodecCapabilities.COLOR_FormatYCbYCr,
            CodecCapabilities.COLOR_FormatYCrYCb, CodecCapabilities.COLOR_FormatCbYCrY,
            CodecCapabilities.COLOR_FormatCrYCbY -> VideoDetails(ColorDepth.EIGHT, ChromaFormat.YUV422)
            CodecCapabilities.COLOR_FormatYUV444Flexible, CodecCapabilities.COLOR_FormatYUV444Interleaved ->
                VideoDetails(ColorDepth.EIGHT, ChromaFormat.YUV444)
            else -> VideoDetails()
        }
    }
}

internal inline fun <T> safely(block: () -> T): T? = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}
internal fun MediaFormat.intOrNull(key: String): Int? = safely { if (containsKey(key)) getInteger(key) else null }
internal fun MediaFormat.longOrNull(key: String): Long? = safely { if (containsKey(key)) getLong(key) else null }
internal fun MediaFormat.stringOrNull(key: String): String? = safely { if (containsKey(key)) getString(key) else null }
internal fun MediaFormat.numberOrNull(key: String): Float? = safely { if (containsKey(key)) getFloat(key) else null }
    ?: intOrNull(key)?.toFloat()
