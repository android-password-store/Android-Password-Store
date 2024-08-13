#!/usr/bin/env bash

set -e
set -u
set -o pipefail

ZERO="0000000000000000000000000000000000000000"
GRADLE_EXEC="${GRADLE_EXEC:-./gradlew}"

while read -r local_ref local_oid remote_ref remote_oid; do
  # These useless assignments are to silence warnings from shellcheck about unused variables
  _=$local_ref
  _=$remote_ref
  _=$remote_oid
  if [ "${local_oid}" != "${ZERO}" ]; then
    CI=true "${GRADLE_EXEC}" metalavaCheckCompatibilityRelease lint spotlessCheck test -PslimTests
  fi
done
