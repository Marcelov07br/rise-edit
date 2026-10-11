# Validação — Rise Edit 0.2.0

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
