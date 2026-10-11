# Rise Edit

Editor Android nativo, em Kotlin e Jetpack Compose. MVP 0.1.0: importação de vídeos, reprodução da timeline, aparagem, divisão, reordenação, remoção, desfazer/refazer, estabilização global experimental e exportação MP4.

## Usar

1. Instale o APK **release assinado**, não o APK debug da integração contínua.
2. Toque em **Importar** e selecione vídeos pelo seletor de documentos do Android.
3. Selecione um clipe na timeline. Ajuste o intervalo e aplique o corte; para dividir, posicione o cursor dentro do clipe.
4. Opcionalmente, toque em **Analisar e estabilizar**. Depois escolha suavização ou tentativa de câmera fixa, intensidade, suavidade e limite de zoom.
5. Toque em **Exportar 720p** e escolha um arquivo novo. Os originais nunca são editados.

O projeto é salvo automaticamente no armazenamento privado do aparelho. Permissões de leitura dos vídeos são persistidas. Mover ou apagar o vídeo original, revogar acesso ou desinstalar o app pode impedir a recuperação do projeto. Há um projeto local por instalação nesta primeira versão; desfazer/refazer vale durante a sessão.

## Atualizações pelo GitHub

Em **Configurações**, habilite a busca ao abrir e conecte o GitHub com um token *fine-grained* limitado a `Marcelov07br/rise-edit`, permissão **Contents: Read-only**. Não use um token de administração nem coloque tokens no código. O token é cifrado com AES-GCM e chave não exportável do Android Keystore; backup do app está desativado.

O app consulta a última release estável, compara versões numéricas, baixa `rise-edit.apk`, valida o SHA-256 informado pelo GitHub e confirma package name, versionCode crescente e o mesmo certificado de assinatura. Downloads usam HTTPS e não encaminham a credencial ao host de arquivos. Um botão abre o instalador do Android; o usuário precisa autorizar essa origem e confirmar a instalação. Não há instalação silenciosa nem serviço de verificação em segundo plano: a busca automática acontece ao iniciar o app. O repositório privado exige essa conexão por aparelho.

Para publicar as próximas versões, aumente **versionCode** e **versionName** em `app/build.gradle.kts`, faça commit e envie uma tag correspondente, por exemplo `v0.1.1`. `.github/workflows/release.yml` testa, compila com a mesma chave e cria uma release com o APK e checksum. Tags e releases são o canal de atualização; commits isolados não são APKs instaláveis.

A chave local de distribuição fica em `.signing/`, ignorada pelo Git. **Preserve essa pasta em backup privado**: perder a chave impede atualizar instalações existentes. Configure no GitHub Actions os secrets `RISE_SIGNING_KEYSTORE_B64`, `RISE_KEYSTORE_PASSWORD` e `RISE_KEY_PASSWORD` com a chave inicial. Eles nunca devem entrar em arquivos versionados. O fluxo falha sem esses secrets, em vez de gerar outra assinatura.

## Compilar

Requisitos: JDK 17, Android SDK 35, Build Tools 35.0.0, acesso aos repositórios oficiais Google/Maven Central.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
# Com .signing/signing.properties ou variáveis RISE_KEYSTORE_PATH,
# RISE_KEYSTORE_PASSWORD e RISE_KEY_PASSWORD:
./gradlew :app:assembleRelease :app:lintRelease
```

O wrapper Gradle 8.11.1 está incluído. `local.properties` deve apontar para seu SDK e é ignorado pelo Git. Em Windows com pasta de usuário acentuada, veja `tools/build-windows.ps1` e `docs/VALIDATION.md`.

## Estabilização: o que está implementado

OpenCV 4.10 rastreia pontos com Lucas–Kanade e estima uma transformação parcial afim com RANSAC, em quadros reduzidos de até 320 pixels e aproximadamente 12 amostras/s. Escala estimada é usada somente para rejeitar mudanças grandes; a correção aplicada é rígida, por deslocamento e rotação. A trajetória acumulada é salva separadamente; mudar controles ou dividir o mesmo arquivo reutiliza a análise. Falhas de rastreamento iniciam outro segmento para evitar suavização entre cortes.

Media3 1.9 aplica um efeito de matriz próprio na prévia e na exportação. A suavização usa uma janela temporal; a tentativa de câmera fixa referencia o início de cada segmento do trecho. O zoom uniforme necessário é limitado a 1–1,25×; se a correção exceder esse limite, sua amplitude diminui. Não há deformação local, malha, remoção de rolling shutter ou análise por regiões nesta versão.

Limites: análise de fontes de até 2 minutos; amostragem pode perder tremores de alta frequência, movimentos rápidos ou quadros com pouca textura. Pessoas/objetos grandes em movimento podem dominar a estimativa. Não há garantia de câmera fixa. Exportação H.264/AAC com altura de 720 pixels, SDR; codec, formato, HDR e mistura de orientações precisam de validação nos aparelhos alvo. O processamento deve continuar com o app aberto nesta primeira versão.

## Estrutura

- `core`: edição não destrutiva, operações de timeline, matemática de trajetória, política de versões e testes JVM.
- `app`: interface, armazenamento JSON atômico, SAF, OpenCV, Media3, exportação e atualizador.
- `.github/workflows`: checks e distribuição assinada.
- `docs/VALIDATION.md`: evidências e limites da validação desta entrega.

Documentação de base: [composição e exportação Media3](https://developer.android.com/media/media3/transformer/composition), [estimativa afim OpenCV](https://docs.opencv.org/4.x/d9/d0c/group__calib3d.html), [digests dos assets GitHub](https://docs.github.com/en/rest/releases/assets).
