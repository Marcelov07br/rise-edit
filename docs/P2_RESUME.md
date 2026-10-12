# P2 — pausa solicitada por limite de tokens

Pausa em 12/10/2026, antes de integrar ou publicar a 0.4.2. Release pública continua 0.4.1; PR #4 permanece em rascunho: https://github.com/Marcelov07br/rise-edit/pull/4.

## Estado salvo

- Código do app: refatoração P2 no commit c205b330d763361452b38f93c2c92858aaf66a70. Versão preparada 0.4.2/code 7; package, JSON 3, assinatura original, R8 e ABIs ARM preservados.
- Sete commits da timeline v1 preservados, sem conexão com a UI ou alteração do JSON.
- Último ajuste da instrumentação: a2d0a468a9dc91c76a075b56866ce109139a0e02. Mudanças posteriores a c205b33 são do helper de testes/documentação, não do código do app.
- Local: build/lint Debug e Release, instrumentação Debug/MinifiedTest, detekt, CLI ktlint e 50 testes JVM aprovados. APK local assinado conferido; não é um APK publicado.
- Execuções Android anteriores: 17 testes por variante; a única falha inicial foi o novo ToolPanelFlowTest. Os outros 16, incluindo cancelamento/reexportação, passaram. Os testes antigos HomeFlowTest e VideoPipelineTest não foram alterados.
- O helper novo foi corrigido para labels exatos, ancestrais clicáveis, coordenadas da barra, espera de fechamento e rolagem lenta bidirecional. A validação integral ainda está pendente.
- Run atual: https://github.com/Marcelov07br/rise-edit/actions/runs/38319582253. Última consulta antes da pausa: build verde; Debug e MinifiedTest em execução. A execução pode concluir no GitHub após a pausa, mas não publica a branch da PR.

## Retomada

1. Consultar o resultado desse run. Se houver falha, ler logs/relatórios antes de alterar código; não remover asserções nem editar os testes antigos para aprovar.
2. Aprovar os 17 testes em Debug e MinifiedTest, junto com qualidade/JVM/build/lint. Finalizar VALIDATION.md e docs/releases/0.4.2.md, que ainda registram preparação.
3. Integrar PR #4 preservando os sete commits da timeline; somente depois deixar o workflow de main publicar 0.4.2.
4. Baixar o APK publicado e conferir SHA-256, certificado original, versão/code, ABIs e limite de 60 MB. Instalação física ARM e retenção de projetos em atualização permanecem não executadas.

Branch local/remota: codex/p2-editor-final. Gradlew.bat tem uma alteração antiga apenas de EOL; foi preservada e excluída dos uploads. Não incluir .signing, .tmp, artifacts ou os APKs analisados em commits. Git CLI está autenticado com outra conta para escrita; os commits remotos foram enviados pelo conector GitHub, e fetch anônimo funciona.
