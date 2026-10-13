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

## P1 — prévia separada

- `preview/PreviewEngine` possui um único player ativo. Rápida usa ExoPlayer sem composição GL; Fiel usa CompositionPlayer com preparação adiada em 200 ms, controle de decoders, guarda de erro e watchdog de 3 s durante reprodução. Falha volta à Rápida mantendo cursor e intenção de reprodução.
- `PreviewTimeline` conserva posição global, cortes e duração de slots ausentes; `FastPreview` troca um clipe indisponível por silêncio na mesma posição, permitindo acessar os demais. Velocidade usa a curva existente; Fiel e export continuam com a composição completa.
- `PreviewPanel` aplica transformação, estabilização e cor aproximadas no TextureView da Rápida. Fiel usa SurfaceView, exigência da API experimental atual. Camadas, texto e áudio extra são vistos na Fiel; a limitação aparece na interface.
- A Rápida prefere proxy existente, mas não inventa decodificação em 480p: o ExoPlayer decodifica a fonte efetiva. Não criamos um novo proxy de todas as fontes compatíveis somente para impor 480p. A estabilização reutiliza a análise já salva e a comparação original.
- Importação/análise/export liberam os recursos do preview; cancelamento aguarda a limpeza antes de preparar novamente. O lifecycle pausa pelo proprietário do player para evitar retomar reprodução em segundo plano.
- Export com overlays verifica orçamento de decoders e fornece ação se excedido. Não implementa composição sequencial/bake de camadas nesta entrega.

## Evidência

Comandos e resultados executados em VALIDATION.md. Corpus pequeno e reproduzível em MEDIA_FIXTURES.md. APV não tem fixture nem validação física. Não há garantia de reproduzir todos os formatos em todos os aparelhos.

## P3 — otimização do APK

- Release: R8 com regras padrão de otimização e remoção de recursos não usados; APK contém ARM64 e ARMv7. Debug conserva todas as ABIs.
- OpenCV é preservado por causa do JNI. Nesta primeira release com R8, preservamos o contrato público do app/modelo, Kotlin, coroutines e tracing compartilhados com a instrumentação; Media3/Compose e recursos continuam sujeitos à redução. Regras mais estreitas ficam fora desta etapa.
- `@raw/transparent` é mantido explicitamente porque o compositor o acessa por URI. Não substituir por gaps nativos de vídeo: a composição anterior verificou que eles não funcionam nesta versão do Media3.
- Variante `minifiedTest`, habilitada somente por `-PminifiedRuntime=true`, herda R8/shrinking da release, mas usa assinatura de debug e x86_64 para o emulador. Ela não é publicada. O CI executa os mesmos testes em Debug e MinifiedTest, incluindo análise OpenCV e três exportações.
- O teste de binding visual adicional verifica pixels reais na TextureView/SurfaceView e a pausa ao sair do editor. Os testes existentes não foram modificados.
- Primeira execução encontrou a ausência de `androidx.tracing.Trace` na instrumentação otimizada e um quadro preto ao trocar Fiel→Rápida com o player pausado. Foram adicionadas regras de tracing e binding por identidade do player, com nova solicitação do quadro após a superfície real existir. Debug e MinifiedTest são executados em emuladores separados; a correção continua em validação.
- Base medida: APK publicado 0.4.0, 146.352.977 bytes. Release otimizada local final: 41.443.641 bytes, redução de 71,7%; a meta de menos de 60 MB foi atingida. O deploy recusa um APK acima desse limite. Certificado original e ARM64/ARMv7 conferidos; os seis binários nativos ARM são idênticos aos da 0.4.0.
- Run 38203918232: 15 testes com R8 aprovados sem skips/falhas, incluindo análise OpenCV, três exportações e pixels reais de preview. Debug passou com 15 testes nas execuções anteriores; neste run foi repetido porque o servidor do emulador retornou HTTP 502 antes de iniciar os testes. As APIs públicas do player, da janela, do lifecycle e dos builders são preservadas na release e na variante de instrumentação para manter os descritores entre APKs.
- A validação executada usa Android 15/x86_64; instalação em aparelho ARM e atualização com retenção de dados em aparelho físico não foram executadas nesta etapa. A política de otimização foi testada, mas a variante de instrumentação possui ABI e assinatura próprias. A refatoração P2 permanece pendente.
- Validação final do commit 5e7266b, run 38205186498: build verde, 15 testes Debug e 15 com R8 aprovados, sem skips/falhas, incluindo os pixels do controle de exportação após as trocas de preview. P3 concluída com ganho aproximado de 72%; P2 ainda não iniciada.

Referências: [otimização de tamanho](https://developer.android.com/topic/performance/reduce-apk-size), [preservação de recursos](https://developer.android.com/topic/performance/app-optimization/customize-which-resources-to-keep), [ABIs](https://developer.android.com/ndk/guides/abis). A refatoração P2 continua fora desta entrega.

## P2 — base validada, integrada na 0.4.3

- `EditorViewModel` virou fachada; `EditorState`, histórico puro, documento, comandos, abertura/importação, prévia, análise e exportação têm componentes próprios. Save precede publicação/histórico; proxies do projeto atual e do histórico são protegidos.
- `EditorOperations` mantém uma única operação até renderização, cópia e limpeza terminarem. Cancelar impede novos comandos enquanto o Transformer é cancelado e o temporário é removido. A cópia SAF verifica cancelamento entre blocos e fecha ambos os streams.
- Oito painéis com `EditorTool` tipado e dispatch exaustivo. A tela foi dividida em estado local, launchers, efeitos, regiões e diálogos, mantendo layout e textos usados pelos testes.
- `VideoEngine` separa principal/overlays/áudio, efeitos finais, compositor e `TransparentGapItem`. Preservados SpeedPoint, o workaround de velocidade/áudio do Media3, proxy obrigatório e compositor somente com overlays.
- Detekt/ktlint fixados no CI/deploy. Baselines exclusivamente da 0.4.1/0461625; novas funções longas foram corrigidas, não suprimidas. Componentes extraídos têm aproximadamente 120 linhas ou menos; arquivos legados de compatibilidade/prévia permanecem fora desta reorganização. Classe 300, função 60, complexidade 15 e nesting 4 são os limites ativos.
- Sete commits da timeline v1 preservados. A 0.4.3 reutiliza seu adaptador somente leitura para calcular trim com limites de velocidade, mantendo listas antigas e JSON 3. Velocidade/rampa não foi desbloqueada na interface.
- P2 validada: run 38337354805, build/qualidade/JVM e 17 testes por variante Debug/MinifiedTest aprovados, inclusive oito painéis e cancelamento/reexportação.
- A primeira entrega visível da 0.4.3 acrescenta pinça em clipes/faixas vazias, viewport de largura fixa e arestas no vídeo principal, com rascunho e uma transação por gesto. 57 testes JVM; 18 testes por variante no run 38343832131. APK assinado e demonstração real entregues no Drive.
- Próximas entregas: arestas nas demais camadas, ripple/sincronização explícitos e transições nas junções. Escopo e critérios em TIMELINE_INTERACTION_PLAN.md; resultados e limites em VALIDATION.md.

Nota de distribuição P3: APK 0.4.1 efetivamente publicado tem 41.427.257 bytes e SHA-256 `a9904593627185ca5467459db299730041fb32ed7163baffef324ce1cfa7ebfa`; os valores locais anteriores acima são históricos.
