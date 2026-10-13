# Timeline — evolução por entregas utilizáveis

## Problema observado

TimelinePanel concentra desenho, escala, conversão de coordenadas, seleção, navegação e movimentação. A pinça está somente na régua; TimelineItem não tem handles de trim. ProjectCommands.trim depende do índice selecionado. Separar arquivos sem estabelecer contratos de interação não resolve esses problemas.

## Primeira entrega: vídeo principal, pinça e arestas

- Pinça em toda a área das faixas, inclusive sobre clipes, conservando o tempo sob o centro dos dedos.
- Handles visíveis no clipe selecionado: esquerda altera entrada; direita altera saída. Permitir encurtar e recuperar conteúdo disponível da fonte, sem alterar o arquivo original.
- Feedback de duração e limites durante o arraste; confirmação em uma única transação ao soltar. Cancelar o gesto descarta o rascunho.
- Um gesto corresponde a um desfazer. Salvar e reconstruir a composição após confirmação, sem gravação de projeto a cada evento de toque.
- Mesmos limites de fonte no projeto, preview e exportação. Preservar SpeedPoint, keyframes, assinatura e leitura dos projetos existentes.

## Responsabilidades propostas

1. TimelineViewport: escala, origem visível, pan e conversão pixel/tempo. Régua, clipes, cursor e handles compartilham esse cálculo.
2. TimelineGestureController: estados de seleção, pan, pinça, cursor, mover e aparar. Dois dedos assumem o zoom; uma aresta selecionada assume o trim; mover o corpo continua sendo uma ação distinta.
3. ClipView e TrimHandles: desenham o estado e oferecem alvos de toque acessíveis; não salvam projetos nem calculam duração da mídia.
4. TimelineEditSession: rascunho imutável, limites da fonte e cancelamento. Trabalha com IDs estáveis de clipes, não com seleção mutável por índice.
5. Comandos de edição: confirmam a sessão, persistem, atualizam histórico e notificam preview/exportação. Reutilizar os modelos existentes e a base de tempo do core.

## Aceitação da primeira entrega

- Demonstrar no app: importar dois vídeos, pinçar sobre um clipe e sobre uma faixa vazia, arrastar ambas as arestas, desfazer/refazer, reabrir e exportar.
- Conferir visualmente a duração e decodificar o MP4 para confirmar os cortes reais e a preservação das fontes.
- Conferir que pinça, scroll, mover e trim não disparam uns aos outros.
- Entregar APK assinado no Drive e uma gravação curta dos gestos reais. Não declarar funcionalidade com base somente em build ou testes de abertura de painéis.

## Entregas seguintes

2. Aplicar o mesmo contrato a áudio, overlays e texto; consolidar mover, split, snapping e comportamento explícito de ripple e sincronização entre camadas.
3. Modelar transições pela relação entre clipes vizinhos e apresentar o controle na junção. Preview e export devem usar o mesmo modelo; crossfade exige suporte real de sobreposição. Mudanças persistidas terão migração própria, não uma alteração silenciosa do JSON existente.
4. Ligar progressivamente o modelo de trilhas do core ao editor e ao render, com paridade antes de substituir listas antigas. Avaliar desempenho com projetos maiores.

## Forma de trabalho

Usar Edits/VN como referência de comportamento e organização; implementar código próprio. Priorizar provas visíveis, testes de tempo/limites, gestos reais e uma exportação representativa. Build local pode produzir o APK; GitHub Actions não é requisito da autoatualização. Se o usuário pedir pausa por tokens, preservar a última etapa utilizável e registrar o ponto de retomada.
