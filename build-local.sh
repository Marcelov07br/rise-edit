#!/usr/bin/env bash
# Local build. Usage: ./build-local.sh [debug|check|release]
#   debug   (default) core unit tests + debug APK (arm64-v8a + x86_64)
#   check   detekt + ktlint + core tests + lintDebug (same as CI)
#   release signed release APK; needs .signing/signing.properties (gitignored)
#           with storeFile/storePassword/keyPassword, or RISE_KEYSTORE_PATH,
#           RISE_KEYSTORE_PASSWORD, RISE_KEY_PASSWORD in the environment.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/jdk}" ANDROID_HOME="${ANDROID_HOME:-$HOME/android}"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
mode="${1:-debug}"
case "$mode" in
  debug)
    ./gradlew :core:test :app:assembleDebug --console=plain
    echo "APK: app/build/outputs/apk/debug/app-debug.apk" ;;
  check)
    ./gradlew :detekt :ktlintCheck :core:test :app:assembleDebug :app:lintDebug --console=plain ;;
  release)
    if [ ! -f .signing/signing.properties ] && [ -z "${RISE_KEYSTORE_PATH:-}" ]; then
      echo "Missing release key: create .signing/signing.properties or set RISE_KEYSTORE_* (see README)." >&2
      exit 1
    fi
    ./gradlew :detekt :ktlintCheck :core:test :app:assembleRelease :app:lintRelease --console=plain
    echo "APK: app/build/outputs/apk/release/app-release.apk" ;;
  *) echo "Usage: $0 [debug|check|release]" >&2; exit 2 ;;
esac
