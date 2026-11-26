#!/usr/bin/env bash
set -euo pipefail

ADB_ARGS=()
if [[ -n "${ADB_SERIAL:-}" ]]; then
  ADB_ARGS=(-s "${ADB_SERIAL}")
fi

echo "=== ADB Devices ==="
adb devices -l

echo "=== Android Runtime Properties ==="
echo "sys.boot_completed : $(adb "${ADB_ARGS[@]}" shell getprop sys.boot_completed | tr -d '\r')"
echo "Android release    : $(adb "${ADB_ARGS[@]}" shell getprop ro.build.version.release | tr -d '\r')"
echo "SDK API level      : $(adb "${ADB_ARGS[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
echo "CPU ABI            : $(adb "${ADB_ARGS[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
echo "Display size       : $(adb "${ADB_ARGS[@]}" shell wm size | tr -d '\r')"
echo "Display density    : $(adb "${ADB_ARGS[@]}" shell wm density | tr -d '\r')"
