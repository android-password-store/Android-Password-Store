#!/usr/bin/env bash
#
# Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
# SPDX-License-Identifier: GPL-3.0-only
#

set -ex

# Simple script that uses OpenSSL to encrypt a provided file with a provided key, and writes the result
# to the provided path. Yes it's very needy.

INPUT_FILE=$1
OUTPUT_FILE=$2
ENCRYPT_KEY=$3

if [[ -n "$ENCRYPT_KEY" && -n "$INPUT_FILE" && -n "$OUTPUT_FILE" ]]; then
    openssl enc -aes-256-cbc -md sha256 -pbkdf2 -e -in "${INPUT_FILE}" -out "${OUTPUT_FILE}" -k "${ENCRYPT_KEY}"
else
    echo "Usage: ./encrypt-secret.sh <input file> <output file> <encryption key>"
fi
