#!/usr/bin/env bash

ENCRYPT_KEY=$1

if [[ -n "$ENCRYPT_KEY" ]]; then
    # Decrypt Release key
    openssl enc -aes-256-cbc -md sha256 -pbkdf2 -d -in release/keystore.cipher -out keystore.jks -k "${ENCRYPT_KEY}"

    # Decrypt signing config
    openssl enc -aes-256-cbc -md sha256 -pbkdf2 -d -in release/props.cipher -out keystore.properties -k "${ENCRYPT_KEY}"
else
    echo "ENCRYPT_KEY is empty"
fi
