# Rise Edit

Editor Android nativo, em Kotlin e Jetpack Compose. Versão 0.11.0: excluir com ripple, marcadores, cortes J/L, copiar/colar atributos, exportação 4K/HEVC com bitrate livre, log/LUT/curvas com histograma/forma de onda/zebra, legendas automáticas com Whisper no aparelho, experimento de telemetria de giroscópio; 0.10.0: separar áudio, edge trim em overlay/áudio, orientação (girar/espelhar/Ajustar×Preencher), mini janela de quadro-chave com curvas; prévia Fiel padrão, seleção tipada (clip/áudio/overlay/texto), keyframes por parâmetro, recuperação de mídia ausente, proxies leves de prévia, exportação em segundo plano, timeline com overlays/texto/áudio (mudo por faixa), estabilização experimental e registro de erro exportável.

Arquitetura, recursos e limites da evolução: [docs/EDITOR_ARCHITECTURE.md](docs/EDITOR_ARCHITECTURE.md).

## Usar

1. Instale o APK **release assinado**, não o APK debug da integração contínua.
2. Crie ou abra um projeto na Home, toque em **+ Vídeo** e selecione vídeos pelo seletor de documentos do Android.
3. Selecione um clipe na timeline. Ajuste o intervalo e aplique o corte; para dividir, posicione o cursor dentro do clipe.
4. Opcionalmente, toque em **Analisar e estabilizar**. Depois escolha suavização ou tentativa de câmera fixa, intensidade, suavidade e limite de zoom.
5. Toque em **Exportar** e escolha um arquivo novo. Os originais nunca são editados.

O projeto é salvo automaticamente no armazenamento privado do aparelho. Permissões de leitura dos vídeos são persistidas. Mover ou apagar o vídeo original, revogar acesso ou desinstalar o app pode impedir a recuperação do projeto. A Home gerencia vários projetos com busca, favoritos e ações de renomear, duplicar e excluir. Desfazer/refazer vale durante a sessão.

## Registro de erro

Em **Configurações → Exportar registro de erro**, o app gera um arquivo de texto e abre o menu de compartilhamento do Android. O arquivo reúne os crashes capturados (stack trace + registro do momento), os motivos das últimas saídas do processo (inclui crash nativo no Android 11+), o registro interno do app (buffer circular salvo em arquivo) e o logcat do próprio app. Não precisa de adb.

## Atualizações pelo GitHub

Em **Configurações**, habilite a busca ao abrir. O repositório público recebe atualizações sem login. Se o repositório se tornar privado, conecte o GitHub com um token *fine-grained* limitado a `Marcelov07br/rise-edit`, permissão **Contents: Read-only**. Não coloque tokens no código. A credencial opcional é cifrada com AES-GCM e chave não exportável do Android Keystore; backup do app está desativado.

O app consulta a última release estável, compara versões numéricas, baixa `rise-edit.apk`, valida o SHA-256 informado pelo GitHub e confirma package name, versionCode crescente e o mesmo certificado de assinatura. Downloads usam HTTPS e não encaminham a credencial ao host de arquivos. Um botão abre o instalador do Android; o usuário precisa autorizar essa origem e confirmar a instalação. Não há instalação silenciosa nem serviço de verificação em segundo plano: a busca automática acontece ao abrir ou retornar ao app, com intervalo mínimo de seis horas na mesma sessão. A busca manual fica disponível a qualquer momento. O repositório privado exige essa conexão por aparelho.

Para publicar as próximas versões, aumente **versionCode** e **versionName** em `app/build.gradle.kts`, faça commit e envie uma tag correspondente, por exemplo `v0.2.0`. `.github/workflows/release.yml` testa, compila com a mesma chave e publica o APK e checksum na release correspondente, inclusive quando criada pela interface do GitHub. Tags e releases são o canal de atualização; commits isolados não são APKs instaláveis.

A chave local de distribuição fica em `.signing/`, ignorada pelo Git. **Preserve essa pasta em backup privado**: perder a chave impede atualizar instalações existentes. Os secrets de assinatura foram configurados no GitHub Actions em 11/10/2026: `RISE_SIGNING_KEYSTORE_B64`, `RISE_KEYSTORE_PASSWORD` e `RISE_KEY_PASSWORD` com a chave inicial. Eles nunca devem entrar em arquivos versionados. O fluxo falha sem esses secrets, em vez de gerar outra assinatura.

## Compilar

**Compile sempre localmente** com `./build-local.sh` (debug), `./build-local.sh check` (detekt/ktlint/lint, igual ao CI) ou `./build-local.sh release` (exige `.signing/signing.properties`). Não faça push só para esperar o GitHub Actions compilar. O CI no push roda só checagens + APK debug; os testes no emulador rodam manualmente, à noite e em tags `v*`. Veja `AGENTS.md`.

Requisitos: JDK 17, Android SDK 35, Build Tools 35.0.0, acesso aos repositórios oficiais Google/Maven Central.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
# Com .signing/signing.properties ou variáveis RISE_KEYSTORE_PATH,
# RISE_KEYSTORE_PASSWORD e RISE_KEY_PASSWORD:
./gradlew :app:assembleRelease :app:lintRelease
```

O wrapper Gradle 8.11.1 está incluído. `local.properties` deve apontar para seu SDK e é ignorado pelo Git. Em Windows com pasta de usuário acentuada, veja `tools/build-windows.ps1` e `docs/VALIDATION.md`.

## Estabilização: o que está implementado

Desde a 0.4.4 o vídeo é decodificado em sequência pelo MediaCodec (sem um seek por quadro), lendo só a luminância (Y; 10-bit/HDR pelo byte alto), reduzida a até 640 pixels e na orientação de exibição, até ~33 amostras/s. Se o decodificador do aparelho não entregar quadros legíveis, o caminho antigo (MediaMetadataRetriever) é usado. OpenCV 4.10 rastreia pontos com Lucas–Kanade e estima uma transformação parcial afim com RANSAC. Escala estimada é usada somente para rejeitar mudanças grandes; a correção aplicada é rígida, por deslocamento e rotação. A trajetória acumulada é salva separadamente; mudar controles ou dividir o mesmo arquivo reutiliza a análise. A análise também fica em cache por arquivo (uri + tamanho + data de modificação). Perdas de rastreamento de até 1 s são preenchidas interpolando o movimento; perdas maiores iniciam outro segmento para evitar suavização entre cortes.

Media3 1.9 aplica um efeito de matriz próprio na prévia e na exportação. A suavização usa uma janela temporal triangular de custo linear; a tentativa de câmera fixa referencia o início de cada segmento do trecho. O zoom de recorte varia no tempo (máximo local suavizado, com rampas de entrada/saída) dentro do limite de 1–1,25×; se a correção exceder esse limite, sua amplitude diminui. Não há deformação local, malha, remoção de rolling shutter ou análise por regiões nesta versão.

Limites: análise de fontes de até 20 minutos (2 minutos no caminho de compatibilidade); amostragem pode perder tremores de alta frequência, movimentos rápidos ou quadros com pouca textura. Pessoas/objetos grandes em movimento podem dominar a estimativa. Não há garantia de câmera fixa. Exportação H.264/AAC com altura de 480, 720 ou 1080 pixels, SDR; codec, formato, HDR e mistura de orientações precisam de validação nos aparelhos alvo. O processamento deve continuar com o app aberto nesta primeira versão.

## Estrutura

- `core`: edição não destrutiva, operações de timeline, matemática de trajetória, política de versões e testes JVM.
- `app`: interface, armazenamento JSON atômico, SAF, OpenCV, Media3, exportação e atualizador.
- `.github/workflows`: checks e distribuição assinada.
- `docs/VALIDATION.md`: evidências e limites da validação desta entrega.

Documentação de base: [composição e exportação Media3](https://developer.android.com/media/media3/transformer/composition), [estimativa afim OpenCV](https://docs.opencv.org/4.x/d9/d0c/group__calib3d.html), [digests dos assets GitHub](https://docs.github.com/en/rest/releases/assets).
