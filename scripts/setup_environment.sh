#!/usr/bin/env bash

# Installs the latest command line tools and sets up the necessary packages for an Android emulator
# for API level $ANDROID_API_LEVEL, or API 30 if unspecified.

set -euo pipefail

CMDLINE_TOOLS_URL_MAC="https://dl.google.com/android/repository/commandlinetools-mac-6858069_latest.zip"
CMDLINE_TOOLS_URL_LINUX="https://dl.google.com/android/repository/commandlinetools-linux-6858069_latest.zip"

[ -n "${ANDROID_SDK_ROOT:-}" ] || {
  echo "ANDROID_SDK_ROOT must be set to use this script"
  exit
  1
}

if [ "$(uname)" == "Linux" ]; then
  wget "${CMDLINE_TOOLS_URL_LINUX}" -O /tmp/tools.zip -o /dev/null
elif [ "$(uname)" == "Darwin" ]; then
  wget "${CMDLINE_TOOLS_URL_MAC}" -O /tmp/tools.zip -o /dev/null
else
  echo "This script only works on Linux and Mac"
  exit 1
fi

[ -n "${ANDROID_API_LEVEL:-}" ] || { echo "ANDROID_API_LEVEL not defined; defaulting to 30"; }
[ -n "${ANDROID_SYSTEM_IMAGE_TYPE:-}" ] || { echo "ANDROID_SYSTEM_IMAGE_TYPE not defined; defaulting to 'google_apis' "; }

API_LEVEL="${ANDROID_API_LEVEL:-30}"
SYSTEM_IMAGE_TYPE="${ANDROID_SYSTEM_IMAGE_TYPE:-google_apis}"

unzip -qo /tmp/tools.zip -d "${ANDROID_SDK_ROOT}/latest"
mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
if [ -d "${ANDROID_SDK_ROOT}/cmdline-tools" ]; then
  rm -rf "${ANDROID_SDK_ROOT}/cmdline-tools"
fi
mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
mv -v "${ANDROID_SDK_ROOT}/latest/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"

export PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${PATH}"

sdkmanager --install 'build-tools;30.0.3' platform-tools "platforms;android-${API_LEVEL}"
sdkmanager --install emulator
sdkmanager --install "system-images;android-${API_LEVEL};${SYSTEM_IMAGE_TYPE};x86"
