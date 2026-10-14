# Agent instructions (Codex & co.)

- **Always build and test locally** with `./build-local.sh` (debug: core tests + debug APK, ~40 s cold, a few seconds incremental). Use `./build-local.sh check` (detekt, ktlint, lint) before committing.
- **Never push just to see if it compiles.** GitHub Actions is the final gate, not the compiler. Push only code that passed `./build-local.sh check`.
- Emulator/instrumented tests (`tools/android-runtime.sh`) run in CI only on manual dispatch, nightly and on `v*` tags. Trigger manually with `gh workflow run "Android checks"` when touching preview/export/timeline gestures.
- Release signing: `./build-local.sh release` needs `.signing/signing.properties` (gitignored). Never commit keys; CI uses repository secrets.
- Keep `org.gradle.configuration-cache` working: don't read files/env at execution time in build scripts without `providers.*`.
