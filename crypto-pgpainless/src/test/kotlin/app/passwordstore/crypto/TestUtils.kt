/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package app.passwordstore.crypto

object TestUtils {
  fun getArmoredSecretKey() = this::class.java.classLoader.getResource("secret_key").readBytes()
  fun getArmoredPublicKey() = this::class.java.classLoader.getResource("public_key").readBytes()
  fun getArmoredSecretKeyWithMultipleIdentities() =
    this::class.java.classLoader.getResource("secret_key_multiple_identities").readBytes()
  fun getArmoredPublicKeyWithMultipleIdentities() =
    this::class.java.classLoader.getResource("public_key_multiple_identities").readBytes()
}
