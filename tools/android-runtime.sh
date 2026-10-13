#!/usr/bin/env bash
# Preserve both variants' results and collect diagnostics even when a test fails.
variant=${1:-Debug}
if [ "$variant" = All ]; then
  status=0
  bash "$0" Debug || status=$?
  bash "$0" MinifiedTest || status=$?
  exit "$status"
fi
args=()
case "$variant" in
  Debug) ;;
  MinifiedTest) args+=('-PminifiedRuntime=true') ;;
  *) echo "Unknown runtime variant: $variant"; exit 2 ;;
esac
directory="artifacts/runtime/$variant"
mkdir -p "$directory"
adb logcat -c || true
status=0
./gradlew ":app:connected${variant}AndroidTest" "${args[@]}" --no-daemon || status=$?
for shot in home-test home-failure-test editor-empty-test editor-timeline-test preview-fast-test preview-faithful-test preview-ui-failure tool-0 tool-1 tool-2 tool-3 tool-4 tool-5 tool-6 tool-text tool-failure timeline-pinch-clip timeline-trim-left timeline-trim-both timeline-preview-trimmed timeline-export-complete; do
  adb pull "/sdcard/Download/$shot.png" "$directory/$shot.png" || true
done
adb pull /sdcard/Download/timeline-demo.mp4 "$directory/timeline-demo.mp4" || true
adb logcat -d > "$directory/media-runtime.log" || true
exit "$status"
