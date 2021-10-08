#!/usr/bin/env bash

set -e
set -u
set -o pipefail

GRADLE_EXEC="${GRADLE_EXEC:-./gradlew}"

"${GRADLE_EXEC}" spotlessCheck apiCheck
