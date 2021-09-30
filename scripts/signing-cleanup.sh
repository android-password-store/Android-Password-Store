#!/usr/bin/env bash
#
# Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
# SPDX-License-Identifier: GPL-3.0-only
#

set -ex

# Delete Release key
rm -f keystore.jks

# Delete signing config
rm -f keystore.properties
