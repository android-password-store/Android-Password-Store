/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.data.crypto

import com.proton.Gopenpgp.crypto.Key

/** Wraps a Gopenpgp [Key] to implement [KeyPair]. */
public class GPGKeyPair(private val key: Key) : KeyPair {

  init {
    if (!key.isPrivate) throw KeyPairException.PrivateKeyUnavailableException
  }

  override fun getPrivateKey(): ByteArray {
    return key.armor().encodeToByteArray()
  }

  override fun getPublicKey(): ByteArray {
    return key.armoredPublicKey.encodeToByteArray()
  }

  override fun getKeyId(): String {
    return key.hexKeyID
  }
}
