/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
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

  fun getAEADPublicKey() = this::class.java.classLoader.getResource("aead_pub").readBytes()

  fun getAEADSecretKey() = this::class.java.classLoader.getResource("aead_sec").readBytes()

  fun getAEADEncryptedFile() =
    this::class.java.classLoader.getResource("aead_encrypted_file").readBytes()

  enum class AllKeys(val keyMaterial: ByteArray) {
    ARMORED_SEC(getArmoredSecretKey()),
    ARMORED_PUB(getArmoredPublicKey()),
    MULTIPLE_IDENTITIES_SEC(getArmoredSecretKeyWithMultipleIdentities()),
    MULTIPLE_IDENTITIES_PUB(getArmoredPublicKeyWithMultipleIdentities()),
    AEAD_SEC(getAEADSecretKey()),
    AEAD_PUB(getAEADPublicKey()),
  }
}
