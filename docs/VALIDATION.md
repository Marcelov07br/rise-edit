# Validação — Rise Edit

## 0.4.3 — pinça e arestas na timeline

- Código de produção: 09d8905. Calculadores puros de viewport/trim, controlador único de gestos, componentes separados e confirmação por ID/snapshot. JSON 3 e modelos existentes preservados.
- 57 testes JVM aprovados localmente; assembleRelease/lintRelease aprovados. Detekt sem novas violações e ktlint dos arquivos novos/modificados aprovado. Build/qualidade/JVM também verdes na CI dos runs 38343090080 e 38344229618.
- Android 15, run 38343832131: 18 testes Debug e 18 MinifiedTest aprovados, sem skips/falhas. Inclui pinça com dois contatos sobre clipe e faixa vazia, corte das duas arestas, recuperação da fonte, cancelamento, uma edição por gesto, undo/redo, reabertura, tempo de origem no preview e MP4 com duração/quadro válidos. O projeto de gesto contém dois clipes e verifica a preservação de ambos os arquivos originais por SHA-256.
- A primeira asserção de undo comparava com a fixture anterior ao probe, sem SourceInfo. Corrigida para usar o snapshot completo aberto pelo editor; as comparações de histórico foram mantidas. O run acima falhou apenas na regra de tamanho do método novo de teste (61/60); preparação extraída e recompilada com detekt aprovado, sem mudança de produção ou comportamento do teste.
- APK local 0.4.3/code 8: 41.476.642 bytes, br.com.riseedit, ARM64/ARMv7, R8. Assinatura conferida com certificado SHA-256 30c4705483fa494a54863f6862d2c4ddb8967d6d940b02253ee88322e7c39cec; digest do APK 5f62808a23ecf21ec6c6571060029b7891568e21d070ddcb67d825643c396b7a.
- APK enviado e tamanho conferido no Drive. Gravação real de 26,88 s, com pinça e arrastes, decodificada e enviada ao Drive; capturas inspecionadas. Instalação física ARM não foi executada.
- Escopo: arestas para vídeo principal. Áudio/overlays/texto com arestas, contratos de ripple e transições na junção ficam nas próximas entregas descritas em TIMELINE_INTERACTION_PLAN.md.

## P2 — resultado final da preparação

Run 38337354805, commit c0a6ecb: build/qualidade/JVM, 17 testes Debug e 17 MinifiedTest aprovados. A preparação P2 foi concluída; os registros seguintes são históricos. A 0.4.3 integra P2, os sete commits do modelo de timeline v1 e os novos gestos visíveis.

## P2 — preparação 0.4.2

Retomada em 12/10: os runs 38334266231 e 38335184353 ainda falharam no helper de Áudio. A captura `tool-failure.png` do segundo run mostrou a barra realmente deslocada para Áudio/Overlay/Cor/Movimento/Formato, enquanto o dump de UiAutomation ainda devolvia os botões iniciais e coordenadas antigas. O diagnóstico anterior de gesto/posição não explicava sozinho a falha: havia cache de nós virtuais desatualizado. O helper passou a limpar esse cache com UiAutomation.clearCache no Android 33+, antes de cada consulta, preservando todas as asserções. Run 38336017682, commit ea6f900, verifica a correção e coleta capturas dos painéis.

Local: `:detekt :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:lintDebug` aprovado. CLI ktlint 1.5.0: app/core/scripts sem violações novas, usando baselines capturados do commit 0461625. Detekt inicialmente recusou quatro violações novas de funções/complexidade; foram corrigidas em componentes menores sem ampliar baseline. Os testes antigos HomeFlowTest e VideoPipelineTest permanecem sem edição.

Nova cobertura preparada: oito painéis, edição de texto/undo/redo/reabertura e cancelamento pelo ViewModel seguido de reexportação, com verificação do temporário e da fonte original. Compilação não comprova execução desses cenários. Debug/MinifiedTest no emulador e distribuição assinada ainda pendentes nesta preparação.

Run 38313712423, commit c205b33: build/qualidade/JVM aprovados; cada variante executou 17 testes Android e falhou somente no teste novo dos painéis (`Cannot click Áudio`). Ele procurava substring e encontrou o rótulo não clicável da timeline. Helper corrigido para labels exatos, ancestrais clicáveis e rolagem da barra; nova execução pendente. Os outros 16 cenários de cada variante, incluindo cancelamento/reexportação, passaram. Isso não aprova ainda o fluxo completo dos oito painéis.

Release local: assembleRelease/lintRelease e 50 testes JVM diretos aprovados; variante MinifiedTest e instrumentação compilaram com R8. APK local 41.460.410 bytes, package br.com.riseedit/code 7/0.4.2, ARM64/ARMv7; apksigner aprovado com certificado original SHA-256 `30c4705483fa494a54863f6862d2c4ddb8967d6d940b02253ee88322e7c39cec`. Hash local `7a367371c11df32c48c8761b196bf72077cdc3b236924ca39e66e165784bd2c2`; não é o hash de uma publicação já realizada.

Runs 38314817968 e 38315816126 ainda encontraram a falha do helper ao acessar Áudio. O dump de acessibilidade mostrou que a busca genérica de scroll estava movendo as faixas, mantendo a barra de ferramentas parada. O helper passou a usar um gesto curto sobre os limites reais da barra, com labels exatos e ancestrais clicáveis. Esses ajustes alteram somente a instrumentação; o código do APK permanece o mesmo do commit c205b33. Nenhum teste antigo foi alterado para ocultar a falha.

Runs 38316669193 e 38317769305 expuseram outras ambiguidades da automação: posição capturada após fechar o painel, título da estabilização com o mesmo label do botão e gesto que ultrapassava o controle procurado. A correção guarda as coordenadas do texto clicável da barra, espera sua reaparição e rola lentamente nos dois sentidos conforme os botões visíveis. A execução completa continua sendo exigida; não há skips nem remoção das asserções dos painéis.

## P3 — 0.4.1, resultado da otimização

`tools/build-windows.ps1 -Release`: assembleRelease, lintRelease e 23 testes JVM aprovados com R8/shrinking habilitados. APK local: 40.518.463 bytes, contra 146.352.977 bytes da 0.4.0 publicada (72,3% menor). `apksigner verify --print-certs`: aprovado, mesmo certificado SHA-256 30c4705483fa494a54863f6862d2c4ddb8967d6d940b02253ee88322e7c39cec. `aapt dump badging`: br.com.riseedit, versionCode 6, versionName 0.4.1; somente ARM64 e ARMv7.

`:app:assembleMinifiedTest :app:assembleMinifiedTestAndroidTest -PminifiedRuntime=true`: compilação aprovada, incluindo o APK de instrumentação com R8 e a nova verificação de pixels reais. CI executará Debug e MinifiedTest; estabilização/exportação/UI otimizadas ainda não declaradas aprovadas. P2 continua fora desta entrega.

Run inicial 38199709850: build aprovado, Debug com 14/15 testes aprovados; falha no novo teste visual ao retornar Fiel→Rápida pausado. MinifiedTest não iniciou os testes porque R8 removeu a classe de tracing usada pelo runner. Screenshots/logs preservados. Regras e binding de superfície corrigidos; aguardando nova execução. Não conta como validação funcional aprovada.

Run 38200934126: o seek para a mesma posição não resolveu o quadro pausado; a correção passou a re-preparar o player somente após a superfície existir, mantendo o cursor. O runner otimizado encontrou a API `kotlin.LazyKt` removida. As APIs compartilhadas de Kotlin/coroutines/tracing foram preservadas também na release para que os testes exercitem a mesma política de otimização. Novo resultado funcional ainda pendente.

Run 38201162060: Debug com 15/15 testes aprovados após a correção visual; MinifiedTest ainda não iniciou por `LazyKt`. Run 38201624199: Debug novamente 15/15; MinifiedTest iniciou 15 testes, mas APIs usadas pelo APK de instrumentação haviam sido removidas ou tinham descritores alterados por R8 (ExoPlayer builder/analytics, ViewModelProvider e DebugTraceUtil). Regras públicas específicas adicionadas à mesma política da release; aguardando nova execução. Não houve aprovação de exportação otimizada nesta execução.

Run 38202687787: Debug e build aprovados. A instrumentação otimizada avançou para os testes de reprodução, mas encontrou `Timeline.Window.getDurationMs` e a sobrecarga de superfície do CompositionPlayer removidas. APIs públicas de janela/composição foram preservadas; exportação otimizada permanece pendente até a execução completa.

Run 38203359707: os testes otimizados de reprodução/conversão passaram; restaram falhas de API no lifecycle do teste visual e no setter de MIME do builder de exportação. Os contratos públicos correspondentes foram preservados. Testes antigos continuam sem alterações; aguardando a validação integral.

Resultado final executado: commit f3a138c, run 38203918232, MinifiedTest com 15 testes Android aprovados, sem skips/falhas. Inclui sondagem/proxy, reprodução e scrubbing, Fiel/fallback, pixels reais Rápida→Fiel→Rápida e lifecycle, persistência, análise OpenCV e três exports (base estabilizada, áudio/texto, overlay). Debug de mesma UI passou com 15 testes nos runs anteriores; a tentativa Debug deste run teve erro HTTP 502 ao baixar o emulador, sem executar testes, e foi reiniciada isoladamente.

A repetição isolada de Debug passou com 15 testes; run 38203918232 ficou inteiramente verde. Revisão visual adicional observou uma captura Fiel com controles cobertos; o teste novo passou a verificar também o botão Exportar por pixels, e a sincronização de SurfaceView/Compose para API 34 foi habilitada. Esta última verificação visual ainda está em execução; a aprovação anterior não cobre essa asserção nova.

Verificação visual final aprovada: commit 5e7266b, run 38205186498 — build, Debug (15 testes) e MinifiedTest (15 testes) verdes, sem skips/falhas. A nova asserção espera os pixels do controle Exportar após a troca, além dos pixels do vídeo. A captura anterior ocorreu antes da composição visual estabilizar; os controles reaparecem e agora são verificados antes das capturas finais. R8, análise OpenCV, três exportações, proxy e UI foram executados e aprovados. Meta P3 atingida; instalação física ARM permanece não executada.

`tools/build-windows.ps1 -Release` no commit final: assembleRelease/lintRelease e 23 testes JVM aprovados. APK final local: 41.443.641 bytes (41,44 MB), redução de 71,7% sobre a 0.4.0. A primeira medição de 40.518.463 bytes acima é histórica: as regras públicas adicionais aumentaram cerca de 0,93 MB, mantendo a meta de menos de 60 MB. Certificado original preservado, package br.com.riseedit/code 6/0.4.1, apenas ARM64/ARMv7. Hashes dos seis binários ARM comparados com 0.4.0: iguais. Validação física ARM e instalação de atualização em aparelho não executadas; P2 pendente.

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
