# Rise Edit 0.2 — especificação e implementação

## Contenção da entrega

A interface de velocidade/rampa foi adiada para a próxima evolução. O modelo e os testes matemáticos ficam preservados, mas a UI desta versão não oferece essa operação. Media3 1.9 possui uma falha conhecida ao combinar `setSpeed` com remoção de áudio ([issue 3006](https://github.com/androidx/media/issues/3006)); overlays usam timestamps nativos e clipes sem alteração de velocidade não criam processadores de retiming. A prioridade da entrega é projetos, cortes, áudio, texto, estabilização e exportação utilizáveis.

## Estrutura

O app continua nativo: Kotlin, Compose, Media3 1.9 e OpenCV 4.10. `MainActivity` coordena a navegação Home/Editor e as configurações de atualização. `HomeScreen` usa uma grade adaptativa de projetos. `EditorScreen` contém preview, controles, timeline e painéis. `TimelinePanel` representa tempo em uma escala comum; `ToolPanels` altera os dados do projeto, sem manipular fontes. `EditorViewModel` guarda histórico e reconstrói a composição após cada edição confirmada.

`core/Project.kt` define clipes, pontos de velocidade em coordenadas absolutas da fonte, três faixas de áudio, duas camadas de overlay, textos, keyframes e exportação. `core/Timeline.kt` faz snapping e interpolação. A faixa principal é sequencial e fecha espaços automaticamente; camadas adicionais têm posição absoluta na composição. Cortes na faixa principal não deslocam automaticamente áudio e textos.

`VideoEngine` monta sequências Media3 com gaps, mistura áudio e compõe vídeos sobrepostos. O preview usa `MultipleInputVideoGraph` e saída 480p; a exportação usa a mesma construção com resolução e bitrate escolhidos. Isso reduz o custo de renderização da prévia, mas não cria proxies de arquivos. A decodificação continua dependendo do aparelho.

`EditingEffects` aplica velocidade, transformações interpoladas, cor, fade através de preto, envelope de áudio e textos temporizados. A interface de velocidade/rampa fica adiada nesta entrega contida. Transições não sobrepõem clipes: o fade mantém a duração da timeline. FPS é um limite de descarte; fontes de 24 fps não ganham novos quadros para chegar a 60 fps.

## Biblioteca e migração

`ProjectStore` grava cada projeto em `files/projects/<id>.json`, esquema 2, usando `AtomicFile`. IDs são validados antes de formar caminhos. O projeto anterior (`project.json`, esquema 1) é importado com ID fixo uma única vez; o arquivo antigo permanece preservado. Cada projeto possui nome, data, favorito, mídias e edição. Renomear, duplicar e excluir afetam os projetos, não os arquivos de mídia.

Permissões SAF de leitura são persistidas para vídeo e áudio. Duplicatas compartilham as mesmas fontes e análises imutáveis, com arquivos de projeto independentes. A recuperação exige que as fontes continuem acessíveis. O histórico é limitado a 30 estados por sessão e reinicia ao abrir outro projeto.

Análises v1 não são reutilizadas como se fossem v2: uma estabilização antiga sem análise compatível é desativada na abertura e pode ser analisada novamente. As demais edições permanecem. Credenciais de atualização continuam separadas e cifradas com Android Keystore.

## Estabilização Rise

O produto destaca **Estabilização Rise**, com presets Suave, Forte e Câmera fixa, tutorial curto, progresso cancelável, intensidade, janela de suavização, crop limitado e comparação antes/depois. A comparação afeta somente a prévia; exportação continua obedecendo aos ajustes salvos.

O motor próprio estima movimento global rígido, não reproduz a tecnologia proprietária Warp Stabilizer. Na v0.2, a análise passa de 320px/~12 Hz para até 640px/~24 Hz. Lucas–Kanade é verificado nos dois sentidos (erro de retorno <1,5px); RANSAC exige maioria de inliers e cobertura de pelo menos três quadrantes. Estimativas rejeitadas reiniciam um segmento, evitando suavizar através de falhas de rastreamento. A trajetória original fica separada dos parâmetros de correção.

O limite de zoom reduz a amplitude da correção quando necessário. Não há malha óptica, deformação por regiões, homografia, correção de rolling shutter ou garantia de câmera fixa. O limite inicial é de dois minutos por fonte. O aumento de amostragem e as verificações melhoram a estimativa, mas a qualidade precisa ser medida em cenas reais com parallax, movimento de pessoas, pouca textura e zoom de câmera.

## Interação e recursos

- Home: criação, busca, recentes/favoritos, thumbnail real, data, duração, abrir, renomear, duplicar e excluir com confirmação.
- Timeline: principal, dois overlays, textos, três faixas de áudio, thumbnails extraídos da fonte e waveforms RMS reais amostrados em 64 posições. Falhas de decodificação não são substituídas por ondas decorativas.
- Tempo: slider de scrubbing, régua com cursor, zoom por controles e pinça na régua; segurar/arrastar move blocos. Ímã aproxima camadas de limites dos clipes. A faixa principal é reordenada, sem criar buracos.
- Clipe: trim, split, reorder, remover, fade, volume, cor, enquadramento e keyframes de posição/escala/rotação/opacidade.
- Camadas: importar música/voz, volume/fade/trim; textos e legendas manuais com cor/tamanho/posição/animação de fade; picture-in-picture com posição/escala/opacidade/trim.
- Exportação: MP4 H.264/AAC, 480p/720p/1080p, limite 24/30/60 fps, qualidade 4/8/16 Mbps, progresso e cancelamento. Toda a edição exige o app aberto durante processamento.

Não foram implementados reconhecimento automático de fala, stickers, editor de curvas contínuas, gestos de trim nas bordas ou ajustes animados específicos de overlays. Esses itens não aparecem como ferramentas fictícias na interface.

## Validação

Testes JVM verificam conservação de duração, tradução entre coordenadas fonte/timeline sob velocidade, trim em rampas, divisão, interpolação de keyframes, snapping, zoom de estabilização e política de atualização. Testes instrumentados cobrem round-trip completo dos projetos, isolamento de cópias, análise/exportação com áudio+texto+overlay e criação de projeto pela interface.

Resultados efetivamente executados e limitações do ambiente estão em `VALIDATION.md`. Testes compilados não equivalem a execução funcional aprovada.

Referências: [Composições Media3](https://developer.android.com/media/media3/transformer/composition), [compositor de vídeo](https://developer.android.com/media/media3/transformer/videocompositorsettings), [código da versão 1.9.0](https://github.com/androidx/media/tree/1.9.0).
