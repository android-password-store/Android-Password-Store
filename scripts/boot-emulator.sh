#!/usr/bin/env bash

# Creates and boots a display-less emulator (defaulting to Android 8.1, our min SDK)

set -euo pipefail

[ -n "${ANDROID_HOME:-}" ] || {
  echo "ANDROID_HOME must be set to use this script"
  exit 1
}

[ -n "${ANDROID_API_LEVEL:-}" ] || { echo "ANDROID_API_LEVEL not defined; defaulting to 26"; }
API_LEVEL="${ANDROID_API_LEVEL:-26}"

ARCH="x86_64"
if [[ "$(uname -m)" == "arm64" && "$(uname)" == "Darwin" ]]; then
  ARCH="arm64-v8a"
fi

sdkmanager "system-images;android-${API_LEVEL};google_apis;${ARCH}"

echo no | "${ANDROID_HOME}"/cmdline-tools/latest/bin/avdmanager create avd \
  --force \
  -n "Pixel_XL_API_${API_LEVEL}" \
  --abi "google_apis/${ARCH}" \
  --package "system-images;android-${API_LEVEL};google_apis;${ARCH}" \
  --device 'pixel_xl'

"${ANDROID_HOME}"/emulator/emulator \
  -avd "Pixel_XL_API_${API_LEVEL}" \
  -gpu 'swiftshader_indirect' \
  -no-window \
  -noaudio
