#!/usr/bin/env bash
# Preserve the test exit status while collecting screenshots on failures too.
mkdir -p artifacts
status=0
./gradlew :app:connectedDebugAndroidTest --no-daemon || status=$?
for shot in home-test home-failure-test editor-empty-test editor-timeline-test; do
  adb pull "/sdcard/Download/$shot.png" "artifacts/$shot.png" || true
done
adb logcat -d > artifacts/media-runtime.log || true
exit "$status"
