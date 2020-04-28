#!/usr/bin/env bash

trap 'exit 1' SIGINT SIGTERM

[ -z "$(command -v hub)" ] && { echo "hub not installed; aborting!"; exit 1; }
[ -z "${1}" ] && { echo "No tag specified!"; exit 1; }
prev_ref="$(git rev-parse --abbrev-ref HEAD)"
git checkout "${1}"
gradle clean bundleRelease assembleRelease
hub release create "${1}" -a app/build/outputs/apk/release/app-release.apk
git checkout "$prev_ref"
