# Caderno de anotações — Rise Edit

Lista viva de pedidos do Marce e ideias. Marque [x] quando entrar numa versão.

## Pedidos do Marce (14/10/2026, após 0.7.0)
- [ ] **Bug:** Áudio → Gravar trava e fecha o app (crash).
- [x] **Keyframes por parâmetro:** (**0.10.0**: mini janela com curva/excluir/navegar; Substituir/Travar/Duplicar ficam para depois) ao tocar num item da timeline, ícone de keyframe abre mini janela flutuante (Substituir, Quadro-chave, Curva, Travar, Duplicar, Excluir — ref. CapCut).
- [ ] **Áudio:** nenhum áudio importado toca. Botão mute ao lado da faixa de áudio.
- [x] **Separar áudio do vídeo** (desvincular em faixa própria). **0.10.0**
- [x] **Alças de borda (edge trim)** nas camadas de overlay e áudio (**0.10.0**), iguais às do vídeo principal. (Futuro: texto com animação pré-definida terá limite de duração.)
- [x] **Ferramenta Orientação** ao tocar no objeto: Girar 90°, Espelhar, Inverter vertical, Preencher. **0.10.0** (clipe principal)

## Ideias do Dev Compose
- [ ] Curvas de easing editáveis por keyframe (linear, suave, bounce travado).
- [ ] Ondas de áudio desenhadas nas faixas + ducking automático da música sob a voz.
- [ ] Fade in/out de áudio arrastando no canto da faixa.
- [ ] Legendas automáticas (fala → texto, on-device).
- [ ] Formatos 9:16 / 16:9 / 1:1 com um toque; curva de velocidade; proxy automático pra 4K.
- [ ] Estabilização com giroscópio + correção de rolling shutter.
- [ ] Layout: inspector único ligado à seleção, playhead fixo no centro, divisória preview/timeline arrastável.

## Plano diretor — frentes (rodada 2)
Ordem sugerida: 1 + pedidos do Marce → 2 → 3; 4 em paralelo.

### 1. Base sólida
- [ ] Salvamento automático com histórico de versões do projeto.
- [ ] Projeto à prova de corrupção + recuperação após crash.
- [ ] Relatório de erro no app (como no Rise Musique).

### 2. Edição pro
- [ ] Ripple delete (apagar trecho e o resto encostar).
- [ ] Marcadores na timeline; cortes J/L (áudio antes/depois do vídeo).
- [ ] Copiar/colar atributos entre clipes.
- [ ] Cor com curvas e LUTs.

### 3. Diferencial do canal (mobile / câmeras)
- [ ] Estabilização com giroscópio.
- [ ] Suporte a log/HDR de celular com LUT de conversão.
- [ ] Legendas automáticas estilo shorts.
- [ ] Modelos de abertura e de comparação de câmeras (split/lado a lado).

### 4. Fluxo de trabalho
- [ ] Exportar 9:16 e 16:9 do mesmo projeto.
- [ ] Proxy automático pra 4K.
- [ ] Abrir o projeto no PC (futuro).

### 5. Por último (prioridade mais baixa)
- [ ] Comparação de câmeras com sincronização automática pelo áudio (tela dividida / cortina deslizante).
- [ ] Medidores: histograma, forma de onda e zebra de exposição no preview.

---
**Caderno fechado em 14/10/2026** para avaliação. Ordem de execução: pedidos do Marce + frente 1 → 2 → 3 → 4 (em paralelo quando couber) → 5.

---
## Revisão do plano (14/10/2026) — análise do Claude + considerações do Dev Compose
Fonte: avaliação externa (Claude, via leitura do repo) e respostas do Dev Compose. Tamanho do APK **não é restrição** (decisão do Marce).

### P0: destravar
- [x] Relatório de crash no app (primeiro item). **Feito na 0.8.0**: captura de crash + registro circular em arquivo + Configurações → Exportar registro de erro.
- [x] Crash em Áudio → Gravar (**0.8.0**: ciclo de vida do `MediaRecorder` robusto; causa real no S26 ainda a confirmar com o registro exportado). Pista original (retirada): `RecordPanel` entrega `Uri.fromFile(...)` (file://) ao `addAudio`, mas o resto do app espera URIs do SAF com permissão persistida; provável exceção ao tentar persistir permissão/ler o arquivo. Confirmar com o stack trace.
- [x] Áudio importado não toca (**0.8.0**: Fiel como padrão toca as faixas; mudo por faixa na prévia e exportação): provável que a prévia Rápida ignore faixas extras (também explica overlays aproximados). Mute como flag separada do volume, botão ao lado da faixa.
- [x] Testes de regressão de áudio e gravação (permissão de microfone concedida). **0.8.0**

### Decisão de arquitetura a avaliar
- [x] Três renderizadores (Rápida, Fiel, exportação) são a maior fonte de bugs. **0.8.0: Fiel é a prévia padrão.** Proposta: **Fiel como prévia padrão**, Rápida só para scrub na timeline, em vez de duplicar áudio/overlay na Rápida.

### P1: base
- [ ] Inspector único ligado à seleção, **respeitando a barra de subcategorias da 0.7.0** (não voltar a painéis grandes).
- [ ] Modelo de keyframe no `core` (tempo, valor, easing) com uma função única de avaliação para prévia e exportação.
- [ ] Histórico de versões e `.bak` do projeto (autosave atômico já existe).
- [ ] Reconectar mídia ausente (URIs do SAF).
- [ ] Exportação em segundo plano com serviço em primeiro plano.

### P2: pedidos do Marce
- [x] Separar áudio do vídeo de forma não destrutiva (faixa vinculada, "mudo na fonte").
- [x] Edge trim em overlay e áudio (contrato de arestas da 0.4.3).
- [x] Keyframes por parâmetro com mini janela flutuante (depende do inspector e do modelo de keyframe).
- [x] Orientação: girar 90°, espelhar, inverter, **Preencher = cortar para cobrir o quadro** (como no CapCut); como parâmetro de transformação com keyframe.

### P3: edição pro e canal
- [ ] Ripple delete, marcadores, cortes J/L, copiar/colar atributos.
- [ ] Exportar 4K, 60 fps, HEVC, bitrate escolhido (hoje só 480/720/1080 H.264 SDR); 9:16 e 16:9 do mesmo projeto.
- [ ] Proxy automático para 4K.
- [ ] Legendas automáticas: Whisper on-device (modelo pode vir no APK ou baixado; tamanho não é problema).
- [ ] Estabilização por giroscópio: validar com vídeos do S26 e de câmeras de ação; se o celular não gravar giroscópio, considerar modo de gravação próprio do app que salva os dados.
- [ ] Cor com curvas e LUTs; log/HDR de celular.

### Por último
- [ ] Comparação de câmeras com sincronização pelo áudio; histograma, forma de onda e zebra.
- [x] Atualizar o README (ainda diz 0.3.0) junto da próxima versão. **0.8.0**

## Revisão cruzada (issue #5) — ordem final aprovada em 14/10

Fonte: revisão do Codex na [issue #5](https://github.com/Marcelov07br/rise-edit/issues/5) + resposta do Dev Compose. Esta seção substitui a ordem P0–P3 anterior.

**Correções acordadas**
- Crash em Áudio → Gravar: hipótese `Uri.fromFile` **retirada** (o importador só persiste permissão para `content`). Investigar a liberação do `MediaRecorder` (`release()` fora de proteção, falha em `prepare()/start()`) com logcat real do S26.
- Prévia: Fiel como padrão só após passar no teste instrumentado (2 áudios + 2 overlays + trim + 2×, seek/retomada/mute). Rápida fica para scrub. Feedback tátil precisa de um caminho de rascunho na Fiel sem recriar o player.
- Seleção tipada por ID (`None / Clip / Audio / Overlay / Text`), sem edição implícita do último overlay. A barra de subcategorias continua.
- Keyframes: `ParameterTrack<T>` por parâmetro com `TimeDomain` e `Easing` (Hold, Linear, Smooth, CubicBezier). A migração preserva as curvas Hermite antigas com teste de ouro, e a tolerância de 40 ms vira quantização por frame.
- Exportação: 60 fps e três presets de bitrate já existem. Faltam 4K, HEVC e bitrate livre.
- Giroscópio: experimento em P3, só segue se houver ganho medido.

| Prioridade | Ordem |
|---|---|
| P0 | Diagnóstico mínimo com exportar logcat pelo app → gravação → áudio/mute e validar Fiel como padrão → regressões |
| P1 | Recuperação e mídia ausente → seleção única → keyframes/migração → desempenho/proxies → exportação em serviço |
| P2 | Separar áudio → edge trim → orientação → mini janela de keyframes |
| P3 | Edição pro → 4K/HEVC e formatos → cor/medidores → legendas → experimento com giroscópio (**feito na 0.11.0**) |
| Depois | Sincronização multicâmera e integração com PC |

### P0 — status (0.8.0, 14/10)
- [x] Diagnóstico mínimo: `Diagnostics` (crash não tratado → arquivo, buffer circular de 1000 linhas salvo em arquivo, `ApplicationExitInfo` para crash nativo/ANR, logcat do app) e **Configurações → Exportar registro de erro** (menu de compartilhar).
- [x] Gravação: `VoiceRecorder` libera o recorder em toda falha de `prepare()/start()`, `release()` protegido e idempotente, `stop()` logo após `start()` vira mensagem, gravações repetidas, fechar o painel ou sair do app finaliza e insere. Causa real do S26: **não confirmada** — pedir o registro exportado.
- [x] Áudio/mudo: `AudioClip.muted` (separado do volume), botão no painel de áudio/volume, "MUDO" na timeline; respeitado na Fiel e na exportação.
- [x] Fiel como padrão, Rápida como scrub opcional; edição reaproveita o `CompositionPlayer` (posição, play e mudo preservados, debounce mantido); rascunho de gesto aplicado como delta na Fiel; falha da Fiel mostra aviso explícito na prévia.
- [x] Regressões: gate instrumentado (2 áudios + 2 overlays + trim + 2×: seek, retomada, mudo, reuso do player), `VoiceRecorderTest`, `DiagnosticsTest`, `AudioMuteTest` (core).

### P1 — status (0.9.0, 14/10)
- [x] Recuperação e mídia ausente: snapshots de versão no `ProjectStore`, `ProjectRecovery` (missing/reconnect), diálogo Recuperar mídia, banner no editor.
- [x] Seleção tipada: `Selection` = None / Clip / Audio / Overlay / Text; tools só no alvo; submenu Overlay só adiciona; painéis fecham ao perder o alvo.
- [x] Keyframes: `ParameterTrack<T>` + `TimeDomain` + `Easing` (Hold/Linear/Smooth/CubicBezier); migração Hermite com amostras de ouro; quantização por frame no lugar dos 40 ms.
- [x] Proxies de prévia: `Compatibility.previewProxy` via `MediaPreparation` (proxyRequired=false) para Direct pesados.
- [x] Exportação em serviço: `ExportService` foreground + `ExportBus`; editor permanece editável.

### P2 — status (0.10.0, 14/10)
- [x] Separar áudio: faixa vinculada (`linkedClipId`), "mudo na fonte" (`Clip.muted`); trim/mover/dividir do clipe sincronizam; editar o áudio desvincula.
- [x] Edge trim em overlay e áudio (`OverlayTrimSession`, `AudioTrimSession`), mesmo contrato do vídeo principal.
- [x] Orientação: `Orientation` (90°, espelhar, inverter, Ajustar/Preencher) no clipe principal, prévia Fiel = exportação. Ainda sem keyframe de orientação e sem orientação em overlays.
- [x] Mini janela de quadro-chave (painel baixo): curva Manter/Linear/Suave/Bézier, excluir, anterior/próximo. Persistência por parâmetro (`tracks`, schema 7) com migração.

### P3 — status (0.11.0, 14/10)
- [x] Edição pro: **Excluir com ripple** (clipe sai e textos, overlays, áudios e marcadores depois dele encostam; áudio vinculado sai junto), **marcadores** (Raiz → Marcadores: marcar/desmarcar no cursor, anterior/próximo; traço branco na régua), **cortes J/L** no áudio separado (Ajustar áudio → J 0–2 s antes, L 0–2 s depois; sobrevive a corte/mover do clipe), **copiar/colar atributos** (cor, log/LUT/curva, orientação, estabilização, volume, fade; nunca corte/velocidade). Projeto schema 8.
- [x] Exportação: 1440p e **4K**, **HEVC**, taxa de bits livre (2–150 Mbps em passos) + presets Leve/Boa/Alta calculados por resolução/fps/codec. **60 fps só aparece se alguma fonte tiver ≥ 59 fps**; o limite efetivo nunca passa da fonte (`ExportPlan`). Aviso quando a resolução amplia a fonte.
- [x] Cor: **Cor → Log/LUT**: perfil log (S-Log3, V-Log, Apple Log com fórmulas publicadas; Log genérico aproximado) → LUT .cube importado (copiado para o app, intensidade 25–100%) → curva fixa (S suave/forte, Desbotado, Sombras +, Altas −). Tudo assado num LUT 33³ na GPU (`SingleColorLut`), igual na Fiel e na exportação. **Cor → Medidores**: histograma, forma de onda e zebra (≥95%) do quadro no cursor com a cor aplicada (CPU, 240 px), % estourado.
- [x] Legendas: **Texto → Legendas** com whisper.cpp nativo (JNI, CPU). Modelos Tiny → Large v3 baixados sob demanda (sem limite de tamanho), idioma PT/auto/EN/ES, job sobrevive ao fechar o painel, cancelar/apagar modelo. Respeita corte e velocidade; vira camadas de texto editáveis.
- [x] Giroscópio (experimento): flag em Configurações. Com ela, Estabilizar mostra se o arquivo tem telemetria (`TelemetryProbe`: CAMM, GoPro GPMF, Sony rtmd, DJI djmd, mett, Samsung SEF). Só investigação; nada usa os dados ainda.

**Ficou para depois (P3)**
- Exportar 9:16 e 16:9 do mesmo projeto numa vez; proxy automático para 4K (proxies P1 continuam).
- HDR na saída (HEVC 10 bits/HLG): a composição ainda converte HDR para SDR. Samsung Log sem fórmula pública: usar o LUT oficial.
- Curvas editáveis por ponto e por canal; cor em overlays; zebra sobre a prévia (hoje no painel).
- Legendas: estilo/animação por palavra (shorts), aceleração GPU/NNAPI, áudio de faixas extras. Velocidade em ARM ainda não medida no S26.
- Giroscópio: confirmar com arquivos reais do S26 (ligar a flag e exportar o registro: lista as caixas do MP4). Referências públicas (Gyroflow) não listam a câmera nativa da Samsung como fonte de giroscópio; GoPro (GPMF), Sony, DJI, Insta360 (CAMM) e apps Android como MotionCam Pro/Open Camera Sensors/FilmIt (CAMM/mett) gravam; Gear 360 usa `udta/acgy`. Próximo passo, se houver ganho: leitor GPMF/CAMM → `MotionAnalysis`. Modo de gravação próprio continua fora do editor.

