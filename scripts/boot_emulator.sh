#!/usr/bin/env bash

# Creates and boots an emulator that exactly matches the one in our CI. It is recommended
# to use this as the target device for screenshot tests.

set -euo pipefail

[ -n "${ANDROID_SDK_ROOT:-}" ] || {
  echo "ANDROID_SDK_ROOT must be set to use this script"
  exit
  1
}
[ -n "${ANDROID_API_LEVEL:-}" ] || { echo "ANDROID_API_LEVEL not defined; defaulting to 30"; }
[ -n "${ANDROID_SYSTEM_IMAGE_TYPE:-}" ] || { echo "ANDROID_SYSTEM_IMAGE_TYPE not defined; defaulting to 'google_apis' "; }

API_LEVEL="${ANDROID_API_LEVEL:-30}"
SYSTEM_IMAGE_TYPE="${ANDROID_SYSTEM_IMAGE_TYPE:-google_apis}"

echo no | "${ANDROID_SDK_ROOT}"/cmdline-tools/latest/bin/avdmanager create avd \
  --force \
  -n "Pixel_XL_API_${API_LEVEL}" \
  --abi "${SYSTEM_IMAGE_TYPE}/x86" \
  --package "system-images;android-${API_LEVEL};${SYSTEM_IMAGE_TYPE};x86" \
  --device 'pixel_xl'

"${ANDROID_SDK_ROOT}"/emulator/emulator \
  -avd "Pixel_XL_API_${API_LEVEL}" \
  -no-window \
  -gpu swiftshader_indirect \
  -no-snapshot \
  -noaudio \
  -no-boot-anim
