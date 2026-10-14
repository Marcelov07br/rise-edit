# Caderno de anotações — Rise Edit

Lista viva de pedidos do Marce e ideias. Marque [x] quando entrar numa versão.

## Pedidos do Marce (14/10/2026, após 0.7.0)
- [ ] **Bug:** Áudio → Gravar trava e fecha o app (crash).
- [ ] **Keyframes por parâmetro:** ao tocar num item da timeline, ícone de keyframe abre mini janela flutuante (Substituir, Quadro-chave, Curva, Travar, Duplicar, Excluir — ref. CapCut).
- [ ] **Áudio:** nenhum áudio importado toca. Botão mute ao lado da faixa de áudio.
- [ ] **Separar áudio do vídeo** (desvincular em faixa própria).
- [ ] **Alças de borda (edge trim)** nas camadas de overlay e áudio, iguais às do vídeo principal. (Futuro: texto com animação pré-definida terá limite de duração.)
- [ ] **Ferramenta Orientação** ao tocar no objeto: Girar 90°, Espelhar, Inverter vertical, Preencher.

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
- [ ] Relatório de crash no app (primeiro item).
- [ ] Crash em Áudio → Gravar. Pista concreta: `RecordPanel` entrega `Uri.fromFile(...)` (file://) ao `addAudio`, mas o resto do app espera URIs do SAF com permissão persistida; provável exceção ao tentar persistir permissão/ler o arquivo. Confirmar com o stack trace.
- [ ] Áudio importado não toca: provável que a prévia Rápida ignore faixas extras (também explica overlays aproximados). Mute como flag separada do volume, botão ao lado da faixa.
- [ ] Testes de regressão de áudio e gravação (permissão de microfone concedida).

### Decisão de arquitetura a avaliar
- [ ] Três renderizadores (Rápida, Fiel, exportação) são a maior fonte de bugs. Proposta: **Fiel como prévia padrão**, Rápida só para scrub na timeline, em vez de duplicar áudio/overlay na Rápida.

### P1: base
- [ ] Inspector único ligado à seleção, **respeitando a barra de subcategorias da 0.7.0** (não voltar a painéis grandes).
- [ ] Modelo de keyframe no `core` (tempo, valor, easing) com uma função única de avaliação para prévia e exportação.
- [ ] Histórico de versões e `.bak` do projeto (autosave atômico já existe).
- [ ] Reconectar mídia ausente (URIs do SAF).
- [ ] Exportação em segundo plano com serviço em primeiro plano.

### P2: pedidos do Marce
- [ ] Separar áudio do vídeo de forma não destrutiva (faixa vinculada, "mudo na fonte").
- [ ] Edge trim em overlay e áudio (contrato de arestas da 0.4.3).
- [ ] Keyframes por parâmetro com mini janela flutuante (depende do inspector e do modelo de keyframe).
- [ ] Orientação: girar 90°, espelhar, inverter, **Preencher = cortar para cobrir o quadro** (como no CapCut); como parâmetro de transformação com keyframe.

### P3: edição pro e canal
- [ ] Ripple delete, marcadores, cortes J/L, copiar/colar atributos.
- [ ] Exportar 4K, 60 fps, HEVC, bitrate escolhido (hoje só 480/720/1080 H.264 SDR); 9:16 e 16:9 do mesmo projeto.
- [ ] Proxy automático para 4K.
- [ ] Legendas automáticas: Whisper on-device (modelo pode vir no APK ou baixado; tamanho não é problema).
- [ ] Estabilização por giroscópio: validar com vídeos do S26 e de câmeras de ação; se o celular não gravar giroscópio, considerar modo de gravação próprio do app que salva os dados.
- [ ] Cor com curvas e LUTs; log/HDR de celular.

### Por último
- [ ] Comparação de câmeras com sincronização pelo áudio; histograma, forma de onda e zebra.
- [x] ~~README diz 0.3.0~~ → atualizar junto da próxima versão.
