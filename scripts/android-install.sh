#!/usr/bin/env bash
set -euo pipefail

APP_ID="${ANDROID_APP_ID:-ide.sketchware}"
APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"
ADB_ARGS=()
if [[ -n "${ADB_SERIAL:-}" ]]; then
  ADB_ARGS=(-s "${ADB_SERIAL}")
fi

if [[ ! -f "${APK_PATH}" ]]; then
  echo "ERROR: APK not found at '${APK_PATH}'. Run './gradlew assembleDebug' first." >&2
  exit 1
fi

echo "Configuring deterministic animation settings for UI automation..."
adb "${ADB_ARGS[@]}" shell settings put global window_animation_scale 0 || true
adb "${ADB_ARGS[@]}" shell settings put global transition_animation_scale 0 || true
adb "${ADB_ARGS[@]}" shell settings put global animator_duration_scale 0 || true
adb "${ADB_ARGS[@]}" shell settings put global stay_on_while_plugged_in 3 || true

echo "Installing ${APK_PATH}..."
adb "${ADB_ARGS[@]}" install -r -g "${APK_PATH}"

if ! adb "${ADB_ARGS[@]}" shell pm list packages | tr -d '\r' | grep -qx "package:${APP_ID}"; then
  echo "ERROR: Package '${APP_ID}' is not installed after adb install." >&2
  exit 1
fi

echo "Granting storage and notification runtime permissions for ${APP_ID}..."
adb "${ADB_ARGS[@]}" shell pm grant "${APP_ID}" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true
adb "${ADB_ARGS[@]}" shell pm grant "${APP_ID}" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb "${ADB_ARGS[@]}" shell pm grant "${APP_ID}" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb "${ADB_ARGS[@]}" shell appops set --uid "${APP_ID}" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true
adb "${ADB_ARGS[@]}" shell appops set "${APP_ID}" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true

echo "Successfully installed and prepared ${APP_ID}."
