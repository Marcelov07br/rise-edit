# Validação da entrega inicial

Data: 11/10/2026. Ambiente: Windows, JDK 17.0.16, Gradle 8.11.1, SDK Android 35. Aplicativo mínimo Android 8.1 (API 27).

## Evidências

- APK release gerado com chave própria RSA 3072, persistida localmente fora do Git. Package: `br.com.riseedit`; versionName: `0.1.1`; versionCode: `2`.
- Lint release passou sem erros. Há avisos de dependências e targetSdk mais antigos que os disponíveis. As versões estão fixadas; a entrega não pressupõe validação dos SDKs mais recentes.
- Oito testes JVM passaram usando JUnit 4.13.2: coordenadas dos cortes, conservação de duração ao dividir, rejeição de divisão vazia, inversão de deslocamento/rotação, limite de zoom, suavização/interpolação, isolamento de segmentos e política de atualização/digests.
- Compilação do APK debug e do APK de instrumentação concluída. O teste `VideoPipelineTest` usa uma fonte sintética de 3 segundos com áudio, analisa movimento, persiste a trajetória, divide um trecho de 2 segundos e verifica a saída MP4 720p com áudio e original preservado.
- Execução instrumentada ainda **pendente**: o emulador Pixel_7/Android 16 não respondeu ao ADB ao buscar propriedades, resultando em `Unknown API Level` e `No compatible devices`. Isso é uma falha de infraestrutura, não um teste funcional aprovado. Não houve validação visual da interface ou medição de melhora em vídeos reais.
- A atualização real entre duas versões ainda **pendente**: requer uma release posterior e credencial privada de leitura configurada no aparelho. A comparação de versões/digests está testada; download, certificado e instalador foram implementados, mas não testados em uma atualização real.

## Particularidades locais do Windows

O diretório temporário virtualizado provocou `Unable to establish loopback connection` no Java. Usar `-Djdk.net.unixdomain.tmpdir=` apontando para o caminho curto 8.3 de `.tmp/` permitiu o build. O executor Gradle de testes encontrou um problema adicional de codificação de caminhos acentuados em seu arquivo de argumentos. Os mesmos testes compilados foram executados diretamente por JUnit; `tools/build-windows.ps1` reproduz esse procedimento. Nenhum teste foi omitido para fabricar resultado positivo.

Comandos de build efetivamente usados:

```text
gradle :app:assembleRelease :app:lintRelease :core:compileTestKotlin --no-daemon
java -cp <classes-main;classes-test;junit;hamcrest;kotlin> org.junit.runner.JUnitCore br.com.riseedit.core.EditingTest br.com.riseedit.core.UpdatePolicyTest
gradle :app:connectedDebugAndroidTest --no-daemon
```

## Antes de uso regular

Validar em um aparelho físico: importação por SAF, persistência após fechar/reabrir, vídeo vertical/horizontal, fontes sem áudio, HDR, cortes próximos de keyframes, exportação e cancelamento. Medir estabilização em cenas reais; verificar perda de rastreamento, bordas e parallax. Confirmar atualização `versionCode 1 → 2` com mesma assinatura e retenção do projeto. Região e malha não estão implementadas.

## Distribuição

O repositório é privado. O build de release automático exige os três secrets de assinatura descritos no README; a chave de distribuição não está versionada. Os três secrets foram configurados em 11/10/2026 com a mesma chave da primeira release. Os checks no GitHub Actions (testes JVM, APK debug e lint) passaram. O APK debug do workflow de checks não deve ser usado no canal de autoatualização.
