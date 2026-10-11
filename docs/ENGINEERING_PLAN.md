# Execução dos planos de engenharia

Base: alterações do usuário na v0.3.0, commit 68cd6e5. Layout fixo, applicationId, permissões SAF, chave/alias e SpeedPoint preservados. Prioridade adotada do PLAN.md: P0 → P1 → P3 → P2; validação precede a próxima etapa.

## P0 — compatibilidade

- `core/SourceInfo`, `Compatibility`, `MediaFault`: fatos, política pura e falhas com ação em português. UNKNOWN não é inventado como 8-bit/4:2:0. APV, 4:2:2/4:4:4, HDR, 12-bit, profundidade desconhecida e fontes grandes pedem normalização. Rotação conserva orientação do proxy.
- `media/MediaProbe` + `CodecSpecificData`: MediaExtractor, SPS AVC/HEVC e VUI HDR; findDecoderForFormat considera formato, tamanho e perfil. Áudio é verificado separadamente. Erros de arquivo são retornados; cancelamento permanece cancelamento.
- `media/DeviceCapability`: inventário real, limites declarados e teste de configure/start de até quatro instâncias AVC, liberadas em finally e cacheadas por build do aparelho. A contagem em baixa resolução é indicativa, não garantia de desempenho ou de codecs mistos.
- `media/ProxyTranscoder` + `ProxyCache`: conversão sequencial H.264/AAC, SDR, até 1080p/30fps, áudio estéreo/48kHz, progresso/cancelamento, publicação atômica, deduplicação entre instâncias. A limpeza LRU tem limite padrão de 512 MiB para arquivos descartáveis; proxies referenciados por projetos são protegidos e podem ultrapassar esse limite. Exclusão de projeto limpa somente proxies sem outras referências.
- `MediaImporter`, `MediaPreparation`, `ProjectStore`, `SourceMetadataJson`: esquema 3 aditivo, sondagem tardia de projetos antigos, URI efetiva na análise/composição/thumbnails. Um arquivo recusado não descarta os demais do lote. Thumbnail/waveform têm falha visível e diagnóstico no log.

## Correções necessárias no diagnóstico fornecido

O plano descreve proxy como caminho universal. Media3 também depende de MediaCodec para **decodificar a entrada**: sem decoder APV/10-bit/4:2:2, a conversão local não é possível nesta stack. O aplicativo recusa a mídia com orientação para conversão externa, em vez de iniciar uma operação impossível. Não há decoder software novo nem upload de vídeos. A aceitação universal de APV permanece pendente, não é declarada aprovada. [Formatos do Transformer](https://developer.android.com/media/media3/transformer/supported-formats).

Existe API pública `maxSupportedInstances` desde API 23; ela é uma indicação, não uma garantia. O teste de instâncias complementa essa indicação. Android AVCProfileMain = 2 e AVCProfileHigh10 = 16; perfil sozinho não determina necessariamente a profundidade real do bitstream. P010 de CodecCapabilities é API 33. [Capacidades](https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities), [perfis](https://developer.android.com/reference/android/media/MediaCodecInfo.CodecProfileLevel).

## Evidência

Comandos e resultados executados em VALIDATION.md. Corpus pequeno e reproduzível em MEDIA_FIXTURES.md. APV não tem fixture nem validação física. Não há garantia de reproduzir todos os formatos em todos os aparelhos.
