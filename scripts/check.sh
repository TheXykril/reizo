#!/usr/bin/env bash
# Runs what CI runs. Gametests run when a display or xvfb-run is available.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew build

if [[ -n "${DISPLAY:-}" || -n "${WAYLAND_DISPLAY:-}" ]]; then
  ./gradlew :fabric:runClientGameTest
elif command -v xvfb-run >/dev/null; then
  LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew :fabric:runClientGameTest
else
  echo "Skipping gametests: no display and no xvfb-run" >&2
fi
