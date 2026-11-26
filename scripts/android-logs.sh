#!/usr/bin/env bash
set -euo pipefail

APP_ID="${ANDROID_APP_ID:-ide.sketchware}"
ARTIFACTS_DIR="${ANDROID_E2E_ARTIFACTS:-artifacts/android-e2e}"
ADB_ARGS=()
if [[ -n "${ADB_SERIAL:-}" ]]; then
  ADB_ARGS=(-s "${ADB_SERIAL}")
fi

mkdir -p "${ARTIFACTS_DIR}/logs"
echo "Collecting Android Logcat..."
adb "${ADB_ARGS[@]}" logcat -d -v threadtime > "${ARTIFACTS_DIR}/logs/logcat-full.txt" || true
grep -E "(${APP_ID}|AndroidRuntime|FATAL|ANR in|Build|CodeProject)" "${ARTIFACTS_DIR}/logs/logcat-full.txt" \
  > "${ARTIFACTS_DIR}/logs/logcat-filtered.txt" || true
grep -E "(FATAL EXCEPTION|Fatal signal|ANR in ${APP_ID})" "${ARTIFACTS_DIR}/logs/logcat-full.txt" \
  > "${ARTIFACTS_DIR}/logs/crashes.txt" || true

echo "Saved logs to ${ARTIFACTS_DIR}/logs/"
