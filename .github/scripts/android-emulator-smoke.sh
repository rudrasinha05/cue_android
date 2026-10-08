#!/usr/bin/env bash
set -euo pipefail

# Android emulator only; no local phone configuration is changed.
./gradlew :app:connectedDebugAndroidTest --stacktrace

original_font="$(adb shell settings get system font_scale | tr -d '\r')"
restore_font() {
    if [[ "$original_font" == "null" || -z "$original_font" ]]; then
        adb shell settings delete system font_scale
    else
        adb shell settings put system font_scale "$original_font"
    fi
}
trap restore_font EXIT

echo "Rerunning Android instrumentation at 1.5x font scale"
adb shell settings put system font_scale 1.5
adb shell am force-stop com.rudrasinha.cue
./gradlew :app:connectedDebugAndroidTest --stacktrace
