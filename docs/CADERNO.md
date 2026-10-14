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
