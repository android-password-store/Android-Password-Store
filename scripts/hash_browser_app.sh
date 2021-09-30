#!/usr/bin/env bash

#
# Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
# SPDX-License-Identifier: GPL-3.0-only
#

set -euo pipefail

PACKAGE_NAME="$(aapt dump badging "$1" | grep package: | grep -Eo " name='[a-zA-Z0-9_\.]*" | cut -c8-)"
HASH="$(apksigner verify --print-certs "$1" | grep "#1 certificate SHA-256" | grep -Eo "[a-f0-9]{64}" | tr -d '\n' | xxd -r -p | base64)"
echo "\"$PACKAGE_NAME\" to arrayOf(\"$HASH\")"

