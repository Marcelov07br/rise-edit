# Verificações de qualidade

Ferramentas fixadas: detekt 1.23.8 (compatível com Kotlin 2.0.21), ktlint-gradle 12.2.0 e ktlint 1.5.0. `./gradlew :detekt :ktlintCheck` é obrigatório no CI e no deploy.

Os baselines vieram exclusivamente do código publicado da 0.4.1, commit `0461625`. Detekt foi gerado com `detektBaseline`. Ktlint foi gerado com o reporter oficial `baseline`, separado por módulo e com caminhos relativos ao módulo. Não foram adicionados problemas novos dos componentes do P2 ou da timeline v1 aos baselines.

Não regenerar o baseline na branch atual para fazer o build passar. Corrigir violações novas e remover entradas antigas quando sua dívida for resolvida. Formatação não modifica o comportamento esperado nem deve exigir editar testes antigos para aprovar a refatoração.

Limites ativos: complexidade ciclomática 15, método 60 linhas, classe 300 linhas, nesting 4. `LargeClass` mede a classe, não o arquivo; detekt 1.23.8 não oferece essa regra nativa de tamanho total de arquivo. O objetivo de ~120 linhas foi aplicado aos componentes extraídos do P2; os arquivos antigos de compatibilidade/prévia continuam fora dessa reorganização e têm dívida explícita.

Windows/JDK 17 neste ambiente: workers Gradle podem falhar antes de iniciar por caminhos acentuados. A CLI de ktlint e o JUnit direto foram usados para verificações locais equivalentes; o CI Linux executa as tarefas Gradle normais. Isso não permite declarar testes Android aprovados apenas por compilarem.
