package br.com.riseedit.media

import android.content.Context
import android.net.Uri
import br.com.riseedit.core.*
import java.io.File

class MediaPreparation(private val context: Context) {
    private val transcoder = ProxyTranscoder(context)

    suspend fun prepare(clip: Clip, progress: (Float, String) -> Unit = { _, _ -> }): Clip {
        val proxy = clip.proxyUri?.let(Uri::parse)
        if (clip.sourceInfo != null &&
            proxy?.scheme == "file" &&
            File(proxy.path.orEmpty()).let { it.isFile && it.length() > 0 }
        ) {
            return clip
        }
        val result = MediaProbe.probe(context, Uri.parse(clip.uri))
        result.fault?.let { throw it }
        val info = result.info ?: throw MediaFault.ImportFailed("O arquivo não contém vídeo.")
        if (!result.decoderFound) {
            throw when {
                info.mime == "video/apv" -> MediaFault.UnsupportedApv
                info.chroma == ChromaFormat.YUV422 || info.chroma == ChromaFormat.YUV444 ->
                    MediaFault.UnsupportedChroma(info.chroma, info.bitDepth.bits, info.mime)
                info.bitDepth.bits >= 10 -> MediaFault.Unsupported10Bit
                else -> MediaFault.MissingDecoder(info.mime)
            }
        }
        if (result.hasAudio && !result.audioDecoderFound) {
            throw MediaFault.MissingDecoder(result.audioMime)
        }
        val source = clip.copy(sourceInfo = info, proxyUri = null, proxyRequired = false)
        val device = DeviceCapability.profile(context)
        return when (val plan = Compatibility.plan(info, result.decoderFound, device)) {
            is SourcePlan.Proxy -> source.copy(
                proxyUri = transcoder.prepare(Uri.parse(clip.uri), info, plan.target, progress)
                    .toString(),
                proxyRequired = true
            )
            SourcePlan.Direct -> {
                // Optional lighter preview proxy: export still uses the original
                // (proxyRequired = false), while Fast/Faithful preferProxy paths use it.
                // 0.19: Configurações › "Proxy automático 4K" off → original only.
                if (!br.com.riseedit.settings.SettingsStore.current.autoProxy4k) return source
                val preview = Compatibility.previewProxy(info) ?: return source
                progress(0f, "Preparando prévia leve…")
                source.copy(
                    proxyUri = transcoder.prepare(
                        Uri.parse(clip.uri),
                        info,
                        preview,
                        progress
                    ).toString(),
                    proxyRequired = false
                )
            }
        }
    }
}

fun MediaFault.displayMessage(): String = "$userMessage $suggestedAction"
