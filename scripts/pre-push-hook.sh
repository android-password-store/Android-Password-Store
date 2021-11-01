#!/usr/bin/env bash

set -e
set -u
set -o pipefail

ZERO=$(git hash-object --stdin </dev/null | tr '[0-9a-f]' '0')
GRADLE_EXEC="${GRADLE_EXEC:-./gradlew}"

while read local_ref local_oid remote_ref remote_oid; do
  if [ "${local_oid}" != "${ZERO}" ]; then
    "${GRADLE_EXEC}" spotlessCheck apiCheck
  fi
done
