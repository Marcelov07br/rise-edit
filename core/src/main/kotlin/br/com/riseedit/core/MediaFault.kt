package br.com.riseedit.core

/** Typed failures expose actionable Portuguese text; detail parameters accept app-authored text only. */
sealed class MediaFault(
    val userMessage: String,
    val suggestedAction: String,
    cause: Throwable? = null
) : RuntimeException(userMessage, cause) {
    data class MissingDecoder(val mime: String? = null) : MediaFault(
        "Este aparelho não possui um decodificador compatível com o arquivo.",
        "Converta o vídeo para H.264, 8 bits e 4:2:0 em outro aparelho ou computador e importe novamente."
    )

    object UnsupportedApv : MediaFault(
        "O formato APV precisa ser convertido antes de ser editado neste aparelho.",
        "Converta o arquivo para H.264, 8 bits e 4:2:0 e importe a cópia convertida."
    )

    /**
     * 0.21: names the exact format (e.g. "H.264 High 4:2:2 · 10 bits") so camera files fail with
     * a clear warning at import instead of a generic error later in preview/export.
     */
    data class UnsupportedChroma(
        val chroma: ChromaFormat,
        val bits: Int = 0,
        val codec: String? = null
    ) : MediaFault(
        "Este aparelho não tem decodificador para ${formatLabel(chroma, bits, codec)}.",
        "Converta para 4:2:0 (H.264 ou HEVC) em outro aparelho ou computador e importe a cópia."
    ) {
        companion object {
            fun formatLabel(chroma: ChromaFormat, bits: Int, codec: String?): String {
                val sub = when (chroma) {
                    ChromaFormat.YUV422 -> "4:2:2"
                    ChromaFormat.YUV444 -> "4:4:4"
                    else -> "esta amostragem de cor"
                }
                val name = when (codec) {
                    "video/avc" -> "H.264 "
                    "video/hevc" -> "HEVC "
                    null -> ""
                    else -> "$codec "
                }
                return "vídeo $name$sub" + if (bits > 0) " · $bits bits" else ""
            }
        }
    }

    object Unsupported10Bit : MediaFault(
        "Este aparelho não consegue editar este vídeo de 10 bits diretamente.",
        "Crie uma cópia H.264 de 8 bits ou converta o vídeo em outro aparelho ou computador."
    )

    class ProxyFailed(val detail: String? = null, cause: Throwable? = null) : MediaFault(
        messageWithDetail("Não foi possível preparar a cópia compatível deste vídeo.", detail),
        "Confira o espaço livre e tente novamente. Se o problema continuar, converta o arquivo para H.264 de 8 bits.",
        cause
    )

    class ImportFailed(val detail: String? = null, cause: Throwable? = null) : MediaFault(
        messageWithDetail("Não foi possível importar este arquivo.", detail),
        "Selecione o arquivo novamente e confira se ele é um vídeo válido e acessível.",
        cause
    )

    /** Player failed during preview (not an import problem); [technical] shown in an expandable detail. */
    class PreviewFailed(val technical: String? = null, cause: Throwable? = null) : MediaFault(
        "Não foi possível reproduzir este clipe na prévia.",
        "Tente a prévia Fiel ou reabra o projeto. O arquivo original não foi alterado.",
        cause
    )

    class AssetUnavailable(val uri: String? = null, cause: Throwable? = null) : MediaFault(
        "Um arquivo deste projeto não está mais disponível.",
        "Restaure o arquivo ou importe uma cópia para continuar a edição.",
        cause
    )

    class AssetDecodeFailed(cause: Throwable? = null) : MediaFault(
        "Não foi possível gerar a miniatura ou a forma de onda deste arquivo.",
        "Confira se o arquivo reproduz e tente importar uma cópia H.264/AAC compatível.",
        cause
    )

    object StabilizationNoTexture : MediaFault(
        "Não foi possível acompanhar o movimento: este trecho não tem detalhes visuais suficientes.",
        "Escolha um trecho bem iluminado, com detalhes visuais, ou use outro vídeo para estabilizar."
    )

    class ExportFailed(val detail: String? = null, cause: Throwable? = null) : MediaFault(
        "Não foi possível concluir a exportação.",
        "Confira o espaço livre e tente exportar em 720p e 30 fps.",
        cause
    )

    object ExportCancelled : MediaFault(
        "A exportação foi cancelada.",
        "Inicie uma nova exportação quando estiver pronto."
    )

    companion object {
        // Callers pass platform failures through cause, never through the user-visible detail.
        private fun messageWithDetail(generic: String, detail: String?): String =
            detail?.trim()?.takeIf { it.isNotEmpty() }?.let { "$generic $it" } ?: generic
    }
}
