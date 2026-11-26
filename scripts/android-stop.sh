#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${REDROID_CONTAINER_NAME:-sketchware-redroid}"
ADB_PORT="${REDROID_ADB_PORT:-5555}"

adb disconnect "127.0.0.1:${ADB_PORT}" >/dev/null 2>&1 || true

if docker ps -a --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  echo "Stopping redroid container '${CONTAINER_NAME}'..."
  docker rm -f "${CONTAINER_NAME}" >/dev/null
  echo "Stopped."
else
  echo "Container '${CONTAINER_NAME}' is not running."
fi
