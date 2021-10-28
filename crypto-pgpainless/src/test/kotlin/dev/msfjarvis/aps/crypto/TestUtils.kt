/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

object TestUtils {
  fun getArmoredPrivateKey() = this::class.java.classLoader.getResource("private_key").readText()
}
