package br.com.riseedit.media

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import br.com.riseedit.core.DeviceProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class CodecProfileSnapshot(val profile: Int, val level: Int)

/** Independent bounds are descriptive; isFormatSupported still validates each width/height/rate pair. */
data class VideoCapabilitySnapshot(
    val minWidth: Int,
    val maxWidth: Int,
    val minHeight: Int,
    val maxHeight: Int,
    val widthAlignment: Int,
    val heightAlignment: Int,
    val minFrameRate: Int,
    val maxFrameRate: Int
)

data class CodecTypeSnapshot(
    val mime: String,
    val colorFormats: List<Int>,
    val profileLevels: List<CodecProfileSnapshot>,
    /** Public API 23 hint; resource availability can make the usable count smaller. */
    val advertisedMaxInstances: Int,
    val requiresSecurePlayback: Boolean,
    val requiresTunneledPlayback: Boolean,
    val video: VideoCapabilitySnapshot? = null
)

data class CodecSnapshot(
    val name: String,
    val isEncoder: Boolean,
    val supportedTypes: List<String>,
    val types: List<CodecTypeSnapshot>,
    /** Null on API 27/28, where Android does not expose these properties. */
    val isHardwareAccelerated: Boolean?,
    val isSoftwareOnly: Boolean?
)

/** Local, lazy capability discovery. No device-model allowlist or startup codec allocation. */
object DeviceCapability {
    private val mutex = Mutex()
    private var cachedProfile: DeviceProfile? = null
    private var cachedSnapshot: List<CodecSnapshot>? = null
    private var cachedBuild: String? = null

    suspend fun profile(context: Context): DeviceProfile = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            val buildKey = buildKey()
            if (cachedBuild == buildKey) cachedProfile?.let { return@withLock it }
            if (cachedBuild != buildKey) {
                cachedSnapshot = null
                cachedProfile = null
                cachedBuild = buildKey
            }
            val codecs = readSnapshot()
            val decoders = codecs.filterNot { it.isEncoder }.flatMap { it.types }.filter {
                !it.requiresSecurePlayback && !it.requiresTunneledPlayback
            }
            val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val storedCount = safely {
                if (preferences.getString("build", null) == buildKey) preferences.getInt("avc_instances", 0)
                    .takeIf { it in 1..MAX_PROBE_INSTANCES } else null
            }
            val measured = storedCount ?: measureAvcInstances()
            val result = DeviceProfile(
                canDecode = decoders.map { it.mime }.toSet(),
                maxDecoders = measured,
                supports10Bit = decoders.any(::supportsTenBit),
                supports422 = decoders.any(::supports422)
            )
            currentCoroutineContext().ensureActive()
            // A zero measurement can be temporary resource pressure. Do not persist/cache that failure.
            if (measured > 0) {
                cachedProfile = result
                if (storedCount == null) safely {
                    preferences.edit().putString("build", buildKey).putInt("avc_instances", measured).apply()
                }
            }
            result
        }
    }

    suspend fun snapshot(): List<CodecSnapshot> = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            val buildKey = buildKey()
            if (cachedBuild != buildKey) {
                cachedBuild = buildKey
                cachedProfile = null
                cachedSnapshot = null
            }
            readSnapshot()
        }
    }

    /** The complete extracted format is queried, including dimensions, rate, profile and audio layout. */
    internal fun findDecoderForFormat(format: MediaFormat): String? = findCodecForFormat(format, encoder = false)

    suspend fun canEncodeAvc(width: Int, height: Int, frameRate: Int = 30, bitrate: Int = 8_000_000): Boolean =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            if (width <= 0 || height <= 0 || frameRate <= 0 || bitrate <= 0) return@withContext false
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            findCodecForFormat(format, encoder = true) != null
        }

    suspend fun canEncodeAac(sampleRate: Int = 48_000, channels: Int = 2, bitrate: Int = 192_000): Boolean =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            if (sampleRate <= 0 || channels <= 0 || bitrate <= 0) return@withContext false
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            }
            findCodecForFormat(format, encoder = true) != null
        }

    private fun findCodecForFormat(format: MediaFormat, encoder: Boolean): String? = safely {
        val mime = format.stringOrNull(MediaFormat.KEY_MIME) ?: return@safely null
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val selected = if (encoder) list.findEncoderForFormat(format) else list.findDecoderForFormat(format)
        selected ?: return@safely null
        val codec = list.codecInfos.firstOrNull { it.name == selected && it.isEncoder == encoder } ?: return@safely null
        val caps = codec.getCapabilitiesForType(mime)
        if (!encoder && (caps.isFeatureRequired(CodecCapabilities.FEATURE_SecurePlayback) ||
                caps.isFeatureRequired(CodecCapabilities.FEATURE_TunneledPlayback))) return@safely null
        if (caps.isFormatSupported(format)) selected else null
    }

    private fun readSnapshot(): List<CodecSnapshot> {
        cachedSnapshot?.let { return it }
        val infos = safely { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList() }.orEmpty()
        val result = infos.mapNotNull { info ->
            safely {
                val supportedTypes = info.supportedTypes.toList()
                CodecSnapshot(
                    name = info.name,
                    isEncoder = info.isEncoder,
                    supportedTypes = supportedTypes,
                    types = supportedTypes.mapNotNull { mime ->
                        safely {
                            val caps = info.getCapabilitiesForType(mime)
                            CodecTypeSnapshot(
                                mime = mime,
                                colorFormats = caps.colorFormats.toList(),
                                profileLevels = caps.profileLevels.map { CodecProfileSnapshot(it.profile, it.level) },
                                advertisedMaxInstances = caps.maxSupportedInstances.coerceAtLeast(0),
                                requiresSecurePlayback = caps.isFeatureRequired(CodecCapabilities.FEATURE_SecurePlayback),
                                requiresTunneledPlayback = caps.isFeatureRequired(CodecCapabilities.FEATURE_TunneledPlayback),
                                video = caps.videoCapabilities?.let { video ->
                                    VideoCapabilitySnapshot(
                                        minWidth = video.supportedWidths.lower,
                                        maxWidth = video.supportedWidths.upper,
                                        minHeight = video.supportedHeights.lower,
                                        maxHeight = video.supportedHeights.upper,
                                        widthAlignment = video.widthAlignment,
                                        heightAlignment = video.heightAlignment,
                                        minFrameRate = video.supportedFrameRates.lower,
                                        maxFrameRate = video.supportedFrameRates.upper
                                    )
                                }
                            )
                        }
                    },
                    isHardwareAccelerated = if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else null,
                    isSoftwareOnly = if (Build.VERSION.SDK_INT >= 29) info.isSoftwareOnly else null
                )
            }
        }
        if (result.isNotEmpty()) cachedSnapshot = result
        return result
    }

    private suspend fun measureAvcInstances(): Int {
        val resources = mutableListOf<ProbeCodec>()
        var started = 0
        try {
            // A real surface also supports decoders which cannot produce byte-buffer output.
            var selected: Pair<String, MediaFormat>? = null
            for ((width, height) in listOf(128 to 128, 176 to 144, 320 to 240, 640 to 360, 1280 to 720)) {
                currentCoroutineContext().ensureActive()
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                    setInteger(MediaFormat.KEY_FRAME_RATE, 24)
                    setInteger(MediaFormat.KEY_PRIORITY, 1)
                }
                val name = findDecoderForFormat(format) ?: continue
                selected = name to format
                break
            }
            val (name, format) = selected ?: return 0
            val advertised = readSnapshot().firstOrNull { it.name == name }?.types
                ?.firstOrNull { it.mime == MediaFormat.MIMETYPE_VIDEO_AVC }?.advertisedMaxInstances ?: MAX_PROBE_INSTANCES
            val bound = advertised.coerceIn(1, MAX_PROBE_INSTANCES)
            repeat(bound) {
                currentCoroutineContext().ensureActive()
                val resource = ProbeCodec()
                resources += resource
                resource.texture = SurfaceTexture(false).also {
                    it.setDefaultBufferSize(format.getInteger(MediaFormat.KEY_WIDTH), format.getInteger(MediaFormat.KEY_HEIGHT))
                }
                resource.surface = Surface(resource.texture)
                resource.codec = MediaCodec.createByCodecName(name)
                currentCoroutineContext().ensureActive()
                resource.codec!!.configure(format, resource.surface, null, 0)
                currentCoroutineContext().ensureActive()
                resource.codec!!.start()
                resource.started = true
                started++
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Retain the successfully started count; the failing instance is released in the same finally.
        } finally {
            for (resource in resources.asReversed()) {
                if (resource.started) runCatching { resource.codec?.stop() }
                runCatching { resource.codec?.release() }
                runCatching { resource.surface?.release() }
                runCatching { resource.texture?.release() }
            }
        }
        // This is allocation/start capacity at a tiny AVC size, not a throughput/mixed-codec guarantee.
        return started
    }

    private class ProbeCodec {
        var codec: MediaCodec? = null
        var texture: SurfaceTexture? = null
        var surface: Surface? = null
        var started = false
    }

    private fun supportsTenBit(type: CodecTypeSnapshot): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && CodecCapabilities.COLOR_FormatYUVP010 in type.colorFormats) return true
        return type.profileLevels.any { (profile, _) ->
            when (type.mime) {
                MediaFormat.MIMETYPE_VIDEO_AVC -> profile == CodecProfileLevel.AVCProfileHigh10 ||
                    profile == CodecProfileLevel.AVCProfileHigh422 || profile == CodecProfileLevel.AVCProfileHigh444
                MediaFormat.MIMETYPE_VIDEO_HEVC -> profile == CodecProfileLevel.HEVCProfileMain10 ||
                    profile == CodecProfileLevel.HEVCProfileMain10HDR10 ||
                    (Build.VERSION.SDK_INT >= 29 && profile == CodecProfileLevel.HEVCProfileMain10HDR10Plus)
                MediaFormat.MIMETYPE_VIDEO_VP9 -> profile == CodecProfileLevel.VP9Profile2 || profile == CodecProfileLevel.VP9Profile3 ||
                    profile == CodecProfileLevel.VP9Profile2HDR || profile == CodecProfileLevel.VP9Profile3HDR ||
                    (Build.VERSION.SDK_INT >= 29 && (profile == CodecProfileLevel.VP9Profile2HDR10Plus || profile == CodecProfileLevel.VP9Profile3HDR10Plus))
                "video/av01" -> Build.VERSION.SDK_INT >= 29 && (profile == CodecProfileLevel.AV1ProfileMain10 ||
                    profile == CodecProfileLevel.AV1ProfileMain10HDR10 || profile == CodecProfileLevel.AV1ProfileMain10HDR10Plus)
                else -> false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun supports422(type: CodecTypeSnapshot): Boolean = type.colorFormats.any { it in setOf(
        CodecCapabilities.COLOR_FormatYUV422Flexible, CodecCapabilities.COLOR_FormatYUV422Planar,
        CodecCapabilities.COLOR_FormatYUV422PackedPlanar, CodecCapabilities.COLOR_FormatYUV422SemiPlanar,
        CodecCapabilities.COLOR_FormatYUV422PackedSemiPlanar, CodecCapabilities.COLOR_FormatYCbYCr,
        CodecCapabilities.COLOR_FormatYCrYCb, CodecCapabilities.COLOR_FormatCbYCrY, CodecCapabilities.COLOR_FormatCrYCbY
    ) } || type.profileLevels.any { (profile, _) ->
        type.mime == MediaFormat.MIMETYPE_VIDEO_AVC && profile == CodecProfileLevel.AVCProfileHigh422
    }

    private fun buildKey() = "$CACHE_VERSION:${Build.FINGERPRINT}:${Build.VERSION.SDK_INT}:${Build.VERSION.INCREMENTAL}"
    private const val PREFERENCES = "rise_codec_capabilities"
    private const val CACHE_VERSION = 1
    private const val MAX_PROBE_INSTANCES = 4
}
