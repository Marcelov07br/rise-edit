# Validação — Rise Edit 0.2.0

## P3 — 0.4.1 em validação

`tools/build-windows.ps1 -Release`: assembleRelease, lintRelease e 23 testes JVM aprovados com R8/shrinking habilitados. APK local: 40.518.463 bytes, contra 146.352.977 bytes da 0.4.0 publicada (72,3% menor). `apksigner verify --print-certs`: aprovado, mesmo certificado SHA-256 30c4705483fa494a54863f6862d2c4ddb8967d6d940b02253ee88322e7c39cec. `aapt dump badging`: br.com.riseedit, versionCode 6, versionName 0.4.1; somente ARM64 e ARMv7.

`:app:assembleMinifiedTest :app:assembleMinifiedTestAndroidTest -PminifiedRuntime=true`: compilação aprovada, incluindo o APK de instrumentação com R8 e a nova verificação de pixels reais. CI executará Debug e MinifiedTest; estabilização/exportação/UI otimizadas ainda não declaradas aprovadas. P2 continua fora desta entrega.

Run inicial 38199709850: build aprovado, Debug com 14/15 testes aprovados; falha no novo teste visual ao retornar Fiel→Rápida pausado. MinifiedTest não iniciou os testes porque R8 removeu a classe de tracing usada pelo runner. Screenshots/logs preservados. Regras e binding de superfície corrigidos; aguardando nova execução. Não conta como validação funcional aprovada.

Run 38200934126: o seek para a mesma posição não resolveu o quadro pausado; a correção passou a re-preparar o player somente após a superfície existir, mantendo o cursor. O runner otimizado encontrou a API `kotlin.LazyKt` removida. As APIs compartilhadas de Kotlin/coroutines/tracing foram preservadas também na release para que os testes exercitem a mesma política de otimização. Novo resultado funcional ainda pendente.

Run 38201162060: Debug com 15/15 testes aprovados após a correção visual; MinifiedTest ainda não iniciou por `LazyKt`. Run 38201624199: Debug novamente 15/15; MinifiedTest iniciou 15 testes, mas APIs usadas pelo APK de instrumentação haviam sido removidas ou tinham descritores alterados por R8 (ExoPlayer builder/analytics, ViewModelProvider e DebugTraceUtil). Regras públicas específicas adicionadas à mesma política da release; aguardando nova execução. Não houve aprovação de exportação otimizada nesta execução.

Run 38202687787: Debug e build aprovados. A instrumentação otimizada avançou para os testes de reprodução, mas encontrou `Timeline.Window.getDurationMs` e a sobrecarga de superfície do CompositionPlayer removidas. APIs públicas de janela/composição foram preservadas; exportação otimizada permanece pendente até a execução completa.

Run 38203359707: os testes otimizados de reprodução/conversão passaram; restaram falhas de API no lifecycle do teste visual e no setter de MIME do builder de exportação. Os contratos públicos correspondentes foram preservados. Testes antigos continuam sem alterações; aguardando a validação integral.

## Engenharia sobre v0.3.0 — P0 e P1 validados para a v0.4.0

`tools/build-windows.ps1`: assembleDebug/lintDebug e 23 testes JVM aprovados. `:app:compileDebugAndroidTestKotlin` e `:app:assembleRelease` aprovados. GitHub Actions run 38177601938, commit 7bd85bd: build e android-runtime aprovados, oito testes Android sem falha nem skip; os testes anteriores permanecem sem edição.

P0: 11 arquivos sintéticos, 237.230 bytes, decodificados no FFmpeg sem erros. No Android API 35, H.264, HEVC 8-bit/MKV, VP9/WebM e AV1/MKV foram convertidos para H.264 e realmente reproduzidos pelo ExoPlayer. AVC 10-bit, AVC 4:2:2, HEVC 10-bit e HEVC HDR sem decoder foram recusados com falhas tipadas; metadados corretos foram verificados. MP3, Opus e PCM tiveram decoder reconhecido. APV não foi gerado nem validado. P1 adiciona um vídeo 720p, totalizando 12 arquivos e 581.761 bytes. Detalhes em MEDIA_FIXTURES.md; limitações em ENGINEERING_PLAN.md.

APK baseline assinado: 146.335.665 bytes; certificado SHA-256 30c4705483fa494a54863f6862d2c4ddb8967d6d940b02253ee88322e7c39cec.

P1: run 38178856495, commit ab5ad57, build e android-runtime aprovados. 14 testes Android executados, sem falhas nem skips: inclui frames reais em 720p, scrubbing, transição entre clips aparados, slots ausentes, fallback Fiel→Rápida, esquema 3 e leitura de esquema 2. Os testes anteriores de Home e exportação não foram alterados. Build/lint debug e 23 testes JVM também passaram. Isso não comprova FPS de tela nem a meta de 1,5 s em aparelho físico.

Entrega contida 0.4.0: publica P0/P1. P2 (refatoração/static checks) e P3 (R8/ABIs/tamanho) não foram implementados nem declarados concluídos. O teste adicional de binding visual ainda é um rascunho local e não faz parte desta release.

Os resultados da v0.2 abaixo são históricos e não aprovam automaticamente esta alteração.

Data: 11/10/2026. Android nativo Kotlin/Compose, Media3 1.9, OpenCV 4.10. Android mínimo 8.1.

- APK release assinado: br.com.riseedit, versionName 0.2.0, versionCode 3. Mesmo certificado das versões 0.1.0 e 0.1.1.
- Build release, lint sem erros e 13 testes JVM aprovados no Windows/JDK 17; os checks equivalentes passaram no GitHub Actions.
- Android 15/API 35 no GitHub Actions: persistência completa e isolamento de projetos aprovados. Home/criação/reabertura aprovadas, com capturas reais inspecionadas.
- Exportação instrumentada aprovada em três cenários: vídeo aparado/dividido/estabilizado; vídeo com áudio e texto; composição completa com vídeo sobreposto. Cada cenário verificou duração de aproximadamente 2s, MP4 720p com áudio, quadro decodificável e fonte original preservada. Análise de movimento e armazenamento da trajetória também passaram.
- Evidência da exportação: run 38168573087, commit e053c352ba89df26e36baea14eb677a098c8a091. Os três exports passaram; o job completo ficou vermelho porque o Pixel Launcher do emulador apresentou ANR antes do teste da Home. A Home passou nos runs anteriores, incluindo 38168355526. O teste agora trata apenas esse ANR específico da infraestrutura, sem ocultar erros do Rise Edit.
- Corrigidos na investigação: configuração de compositor indevida em exportação com um único vídeo; deadlock de setSpeed com áudio removido no Media3 1.9; PNG inválido dos intervalos de overlay. Velocidade/rampas foram retiradas da UI para conter o escopo.

## Limites

A estabilização estima translação e rotação globais. Não contém malha Warp, correção por regiões, perspectiva ou rolling shutter. Qualidade em gravações reais precisa de avaliação; o teste sintético demonstra execução, não equivalência ao Warp Stabilizer.

Importação SAF, cancelamento, HDR e desempenho prolongado ainda precisam de verificação em aparelho físico. A instalação de uma atualização real com retenção dos projetos permanece pendente. O deploy assinado da v0.1.1 foi baixado e seu certificado/digest foram verificados. A v0.2.0 usa a mesma chave.

## Distribuição e reprodução

Secrets de assinatura configurados no GitHub Actions; chave e senhas fora do Git. Releases v* publicam rise-edit.apk e SHA-256 automaticamente. Repositório público: busca sem token; credencial opcional para acesso privado. Android pede confirmação na instalação.

Windows: tools/build-windows.ps1 -Release executa build/lint e os mesmos testes JUnit diretamente para evitar a falha de codificação do executor Gradle em caminhos acentuados. Android CI: tools/android-runtime.sh preserva o resultado dos testes e coleta capturas, inclusive em falhas. Arquitetura e contenção em EDITOR_ARCHITECTURE.md.

## Verificação final após contenção

Run Android checks 38169012832: build e android-runtime concluídos com sucesso; 13 testes JVM e os três testes instrumentados aprovados na mesma execução. O tratamento específico do ANR do launcher permitiu validar o fluxo Home/Editor sem mascarar falhas do aplicativo.

Run Publish Android update 38169045912: concluído com sucesso. APK efetivamente publicado da v0.2.0 baixado e verificado: versionCode 3; SHA-256 82a6b6482b043866a5bc69eaeebb71d4cfef114741a9f6fc05a8c13b9feb8902; certificado SHA-256 30c4705483fa494a54863f6862d2c4ddb8967d6d940b02253ee88322e7c39cec. A mesma chave foi preservada.
