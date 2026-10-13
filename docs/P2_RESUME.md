# Checkpoint — P2 e interação direta da timeline

Atualizado em 13/10/2026. A pausa antiga por tokens foi retomada. P2 passou no run
38337354805 (c0a6ecb): qualidade/build/JVM e 17 testes por variante Android.

## Entrega 0.4.3

- Produção: 09d8905, versão 0.4.3/code 8. Pinça sobre régua, clipes e faixas vazias;
  navegação horizontal; duas arestas no vídeo principal; rascunho e commit por ID/snapshot.
- 57 testes JVM; build/lint Release, detekt e ktlint aprovados. Run 38343832131:
  18 testes Debug e 18 MinifiedTest aprovados, incluindo gestos reais, cancelamento,
  restauração de fonte, histórico, reabertura, preview e exportação. A regra de tamanho
  do método de teste foi corrigida em f2f75f6; sem mudanças de produção ou de asserções.
- JSON 3, applicationId, chave original, SpeedPoint, R8, ARM64/ARMv7 e os sete commits
  da timeline v1 preservados. Arquivos originais conferidos por hash após os gestos/export.
- APK local assinado e gravação real enviados e conferidos no Drive:
  [APK](https://drive.google.com/file/d/1ne_zP9blu3bO5qwk51x27L-4i9DlMBEk/view?usp=drivesdk),
  [demonstração](https://drive.google.com/file/d/1_0RRSwdfi3s8Ml1-1n8wKpoHA258sDUW/view?usp=drivesdk).
  Digest e detalhes em VALIDATION.md. Instalação física ARM não foi executada.
- A integração de PR #4 preserva o histórico. O workflow de main publica a versão
  correspondente ao build.gradle.kts; verificar o resultado e o binário publicado.

## Próximo escopo

Aplicar o contrato de arestas a áudio, overlays e texto; esclarecer ripple/sincronização;
depois modelar transições na junção. Não declarar esses recursos entregues na 0.4.3.
Plano completo: TIMELINE_INTERACTION_PLAN.md.

Se o usuário pedir pausa por tokens, concluir a contenção da operação atual, registrar
o ponto verificado e parar. Gradlew.bat tem uma alteração antiga de EOL preservada;
não incluí-la em uploads. Não commitar .signing, .tmp, artifacts ou APKs analisados.
