# Timeline v1 — Rise Edit

Modelo Kotlin/JVM próprio, isolado do app. Não substitui `Project`, `Clip`, `Timeline`, armazenamento, esquema JSON 3 ou player. Os tipos novos ficam em `br.com.riseedit.core.timeline`.

## Componentes

- `TimeRange`: intervalo semiaberto em µs, ordenação, interseção e deslocamento; overflow não é convertido silenciosamente em tempo negativo.
- `TimeMap`, `ConstantSpeedMap`, `PiecewiseMap`: conversão bidirecional, velocidade positiva e clamp; busca binária dos trechos. Pontos da fonte continuam absolutos após trim.
- `SegmentSource`, `Segment`: mídia, foto ou gap, separados da posição na timeline. Fontes de mídia validam duração original e limites do corte. Gaps exigem mapa constante de velocidade 1.
- `Track`: segmentos sequenciais, posição global, consultas nos dois sentidos, offsets e gaps. `Located` devolve índice e tempo local. `timelineToSource` e `sourceToTimeline` são nullable: retornam null fora da faixa, para ID ausente ou gap.
- `Composition`: valida IDs globais, nomes por tipo e trilha principal de vídeo; duração é o comprimento da principal. Faixas auxiliares não a prolongam. Para uma principal com início diferente de zero, seu término global é `Track.endUs`, distinto de `Composition.durationUs`.
- `Project.toComposition()`: projeção somente leitura, com uma principal, uma faixa por overlay e uma faixa física por item de áudio. Textos não entram na v1. IDs têm namespaces/índices para representar cópias e fontes compartilhadas sem colisão. O índice lógico de áudio/overlay é conservado na identificação da faixa.
- `FrozenList`: snapshot defensivo usado por mapas, faixas e composições; as propriedades continuam compatíveis com leitura de `List`. Para substituir listas via `copy`, passe `FrozenList(novaLista)`. Mapas personalizados devem obedecer ao contrato de imutabilidade.

## Diferenças e limites da especificação

1. A garantia universal de ida e volta em até 1 µs não é matematicamente possível com velocidades arbitrárias e saída Long. Em 4×, 2 µs da fonte arredondam para 1 µs do destino e voltam a 4 µs. Os testes verificam a garantia no caso 2× de 4 s solicitado, e a quantização explícita no caso 4×. Bordas de trechos arredondados também podem introduzir erro; não há promessa universal de precisão de 1 µs.
2. Mapas cuja duração arredondada é zero ou ultrapassa Long são recusados. Velocidades não positivas, NaN e infinitas também são recusadas. Decimais são usados no mapa constante para não perder inteiros acima de 2^53.
3. `PiecewiseMap` padrão soma as durações arredondadas dos trechos, conforme a proposta. O adaptador usa a opção `cumulativeRounding`: arredonda posições acumuladas em decimal, evitando deriva de milhares de trechos fracionários. Essa opção exige mapas constantes e recusa trechos que colapsariam a zero. A rampa de 4.000 trechos a 1,5× tem 2.666.667 µs, sem acumular o erro de arredondar cada trecho isoladamente.
4. O `Clip` antigo arredonda duração e posição em milissegundos. O modelo novo mantém microssegundos; diferenças de até 1 ms por segmento são esperadas nos casos de paridade testados. Diferenças entre durações arredondadas de vários clipes podem somar na trilha. A adaptação não está conectada ao playback atual, portanto não altera sua temporização.
5. Uma única faixa sequencial por índice de áudio perderia sobreposições válidas do modelo atual. A projeção cria faixas físicas separadas, preservando offsets, origem e índice lógico. Não reordena nem serializa a reprodução dos áudios.
6. `renderFingerprint()` é estrutural: ignora IDs/nomes, considera ordem, principal, fontes, tempos e mapas. Não inclui texto, volume, cor, fades, estabilização, keyframes, posição/opacidade de overlays nem configurações de saída. Não deve controlar sozinho a reconstrução da prévia. Int pode colidir; hash igual não prova igualdade audiovisual. Mapas personalizados sem representação estrutural conhecida são recusados pelo fingerprint.
7. Invariantes de intervalos, segmentos e trilhas falham na construção, antes que dados inválidos entrem em `Composition.validate()`. Projetos vazios continuam representáveis com principal vazia e duração zero; segmentos nunca têm duração zero.

## Validação — 12/10/2026

Foram executados `./gradlew.bat :core:test --no-daemon` ao fim de cada passo. A compilação passou; o executor Gradle falhou antes de iniciar testes com `ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`, problema já conhecido no Windows/JDK 17 com caminho acentuado. Trocar apenas o runtime de testes não resolveu. As mesmas classes compiladas foram executadas diretamente com JUnit 4.13.2, sem modificar testes anteriores ou dependências.

| Passo | Conteúdo | Testes JVM aprovados via JUnit |
| --- | --- | --- |
| 1 | Intervalos | 31 |
| 2 | Mapas constantes e por trechos | 35 |
| 3 | Tipos de fonte | 37 |
| 4 | Segmentos | 39 |
| 5 | Trilhas | 42 |
| 6 | Composição e fingerprint | 45 |
| 7 | Adaptador, paridade e bordas acumuladas | 50 |

A base tinha 28 testes, incluindo o histórico não commitado do P2. Os 22 testes novos cobrem invariantes, overflow, clamp, intervalos que se tocam, bordas de segmentos, imutabilidade de listas, vazio/gap, validação e fingerprint. A paridade usa semente fixa, 180 clipes gerados, o caso original de rampa/trim e 4.000 pontos fracionários. Nenhum teste anterior falhou na execução direta.

Formatação dos arquivos novos: ktlint CLI 1.5.0, aprovado. Não equivale à tarefa agregada `ktlintCheck`/detekt do P2, cujos baselines ainda estão pendentes. A suíte foi repetida após a formatação.

Isso valida o modelo JVM; não valida a refatoração pendente do app/P2, UI Android ou exportação. Não foi gerado nem publicado APK. Os sete commits incluem somente arquivos de `core/src/main` e `core/src/test` relacionados à timeline; alterações anteriores do P2 permanecem fora desses commits.
