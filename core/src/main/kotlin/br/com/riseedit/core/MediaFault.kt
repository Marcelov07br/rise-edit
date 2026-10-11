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

    data class UnsupportedChroma(val chroma: ChromaFormat) : MediaFault(
        "Este arquivo usa um formato de cor incompatível com a edição no aparelho.",
        "Converta o vídeo para 4:2:0 e importe a cópia convertida."
    )

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
