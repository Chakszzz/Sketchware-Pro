#!/usr/bin/env bash
set -euo pipefail

APP_ID="${ANDROID_APP_ID:-ide.sketchware}"
MAESTRO_DIR="${MAESTRO_FLOWS_DIR:-maestro}"
ARTIFACTS_DIR="${ANDROID_E2E_ARTIFACTS:-artifacts/android-e2e}"
APK_PATH="${1:-app/build/outputs/apk/debug/app-debug.apk}"

ADB_ARGS=()
MAESTRO_DEVICE_ARGS=()
if [[ -n "${ADB_SERIAL:-}" ]]; then
  ADB_ARGS=(-s "${ADB_SERIAL}")
  MAESTRO_DEVICE_ARGS=(--device "${ADB_SERIAL}")
fi

mkdir -p "${ARTIFACTS_DIR}/screenshots" "${ARTIFACTS_DIR}/logs" "${ARTIFACTS_DIR}/reports" "${ARTIFACTS_DIR}/maestro" "${ARTIFACTS_DIR}/apk"

if [[ -f "${APK_PATH}" ]]; then
  cp "${APK_PATH}" "${ARTIFACTS_DIR}/apk/" || true
fi

# Capture initial home/boot screenshot
adb "${ADB_ARGS[@]}" exec-out screencap -p > "${ARTIFACTS_DIR}/screenshots/00_device_ready.png" || true

# Clear logcat buffer before test execution
adb "${ADB_ARGS[@]}" logcat -c || true

# Ensure Maestro is available
export PATH="${HOME}/.maestro/bin:${PATH}"
if ! command -v maestro >/dev/null 2>&1; then
  echo "Installing Maestro CLI..."
  curl -Ls "https://get.maestro.mobile.dev" | bash
  export PATH="${HOME}/.maestro/bin:${PATH}"
fi
maestro --version

PASS_COUNT=0
FAIL_COUNT=0
RESULTS_MD=""

for flow in "${MAESTRO_DIR}"/*.yaml; do
  [[ -e "${flow}" ]] || continue
  flow_name="$(basename "${flow}" .yaml)"
  echo "============================================================"
  echo "Running Maestro flow: ${flow_name}"
  echo "============================================================"

  # Run from screenshots dir so takeScreenshot writes directly there
  flow_abs="$(cd "$(dirname "${flow}")" && pwd)/$(basename "${flow}")"
  report_abs="$(cd "${ARTIFACTS_DIR}/reports" && pwd)/${flow_name}.xml"
  debug_abs="$(cd "${ARTIFACTS_DIR}/maestro" && pwd)/${flow_name}"
  mkdir -p "${debug_abs}"

  set +e
  (
    cd "${ARTIFACTS_DIR}/screenshots"
    maestro "${MAESTRO_DEVICE_ARGS[@]}" test \
      --format junit \
      --output "${report_abs}" \
      --debug-output "${debug_abs}" \
      "${flow_abs}"
  )
  status=$?
  set -e

  # Collect screenshots saved by Maestro across debug-output, flow dir, or ~/.maestro/tests
  find "${debug_abs}" "${MAESTRO_DIR}" "${HOME}/.maestro/tests" -maxdepth 4 -type f -name "*.png" \
    -exec cp -n {} "${ARTIFACTS_DIR}/screenshots/" \; 2>/dev/null || true

  if [[ ${status} -eq 0 ]]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    RESULTS_MD+="| \`${flow_name}\` | **PASS** |\n"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    RESULTS_MD+="| \`${flow_name}\` | **FAIL** (exit ${status}) |\n"
    adb "${ADB_ARGS[@]}" exec-out screencap -p > "${ARTIFACTS_DIR}/screenshots/FAIL_${flow_name}.png" || true
  fi
done

# Collect runtime logs and inspect for crashes
./scripts/android-logs.sh

CRASH_LINES=0
if [[ -s "${ARTIFACTS_DIR}/logs/crashes.txt" ]]; then
  CRASH_LINES="$(wc -l < "${ARTIFACTS_DIR}/logs/crashes.txt" | tr -d ' ')"
fi

# Generate final E2E markdown report
REPORT_FILE="${ARTIFACTS_DIR}/reports/e2e-summary.md"
{
  echo "# Android E2E Test Report (\`${APP_ID}\`)"
  echo ""
  echo "## Runtime Environment"
  echo "- **Android Release:** $(adb "${ADB_ARGS[@]}" shell getprop ro.build.version.release | tr -d '\r')"
  echo "- **SDK API Level:** $(adb "${ADB_ARGS[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "- **CPU ABI:** $(adb "${ADB_ARGS[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
  echo "- **Display:** $(adb "${ADB_ARGS[@]}" shell wm size | tr -d '\r') / $(adb "${ADB_ARGS[@]}" shell wm density | tr -d '\r')"
  echo "- **APK:** \`${APK_PATH}\`"
  echo ""
  echo "## Summary"
  echo "- **PASS:** ${PASS_COUNT}"
  echo "- **FAIL:** ${FAIL_COUNT}"
  echo "- **Fatal Crash / ANR Lines in Logcat:** ${CRASH_LINES}"
  echo ""
  echo "## Flow Results"
  echo "| Flow | Status |"
  echo "| --- | --- |"
  printf "%b" "${RESULTS_MD}"
  echo ""
  echo "## Screenshots Captured"
  for img in "${ARTIFACTS_DIR}/screenshots"/*.png; do
    [[ -e "${img}" ]] || continue
    echo "- \`$(basename "${img}")\`"
  done
  if [[ "${CRASH_LINES}" -gt 0 ]]; then
    echo ""
    echo "## Detected Crashes / ANRs"
    echo '```'
    head -n 80 "${ARTIFACTS_DIR}/logs/crashes.txt"
    echo '```'
  fi
} > "${REPORT_FILE}"

cat "${REPORT_FILE}"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "${REPORT_FILE}" >> "${GITHUB_STEP_SUMMARY}"
fi

if [[ ${FAIL_COUNT} -gt 0 || ${CRASH_LINES} -gt 0 ]]; then
  exit 1
fi
