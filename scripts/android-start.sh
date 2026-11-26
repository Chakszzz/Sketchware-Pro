#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${REDROID_CONTAINER_NAME:-sketchware-redroid}"
IMAGE="${REDROID_IMAGE:-redroid/redroid:12.0.0_64only-latest}"
ADB_PORT="${REDROID_ADB_PORT:-5555}"
DATA_VOLUME="${REDROID_DATA_VOLUME:-sketchware_redroid_data}"
CPUS="${REDROID_CPUS:-4}"
MEMORY="${REDROID_MEMORY:-4g}"
WIDTH="${REDROID_WIDTH:-1080}"
HEIGHT="${REDROID_HEIGHT:-2400}"
DPI="${REDROID_DPI:-420}"

# Verify host Binder IPC availability before starting redroid
if [[ ! -e /dev/binder && ! -d /dev/binderfs ]] && ! grep -qw binder /proc/filesystems 2>/dev/null; then
  echo "ERROR: Linux Binder IPC is not available on this host." >&2
  echo "redroid requires CONFIG_ANDROID_BINDER_IPC / binder_linux (e.g. 'sudo modprobe binder_linux devices=\"binder,hwbinder,vndbinder\"')." >&2
  exit 1
fi

if docker ps -a --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  echo "Stopping existing container '${CONTAINER_NAME}'..."
  docker rm -f "${CONTAINER_NAME}" >/dev/null
fi

echo "Starting redroid (${IMAGE}) on 127.0.0.1:${ADB_PORT}..."
docker run -itd --rm \
  --name "${CONTAINER_NAME}" \
  --privileged \
  --cpus="${CPUS}" \
  --memory="${MEMORY}" \
  -v "${DATA_VOLUME}:/data" \
  -p "127.0.0.1:${ADB_PORT}:5555" \
  "${IMAGE}" \
  androidboot.redroid_width="${WIDTH}" \
  androidboot.redroid_height="${HEIGHT}" \
  androidboot.redroid_dpi="${DPI}" \
  androidboot.redroid_gpu_mode=guest

echo "Waiting for ADB at 127.0.0.1:${ADB_PORT}..."
for _ in $(seq 1 60); do
  if adb connect "127.0.0.1:${ADB_PORT}" 2>/dev/null | grep -qE "connected|already connected"; then
    break
  fi
  sleep 2
done

export ADB_SERIAL="127.0.0.1:${ADB_PORT}"
adb -s "${ADB_SERIAL}" wait-for-device

echo "Waiting for Android boot completion..."
for _ in $(seq 1 90); do
  if [[ "$(adb -s "${ADB_SERIAL}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
    echo "redroid booted successfully."
    exit 0
  fi
  sleep 2
done

echo "ERROR: Timed out waiting for sys.boot_completed=1" >&2
exit 1
