#!/usr/bin/env bash
set -ex

# Delete Release key
rm -f keystore.jks

# Delete signing config
rm -f keystore.properties
