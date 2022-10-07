#!/usr/bin/env bash

set -e
set -u
set -o pipefail

ZERO="0000000000000000000000000000000000000000"
GRADLE_EXEC="${GRADLE_EXEC:-./gradlew}"

while read local_ref local_oid remote_ref remote_oid; do
  if [ "${local_oid}" != "${ZERO}" ]; then
    "${GRADLE_EXEC}" apiCheck detekt spotlessCheck test -PslimTests
  fi
done
