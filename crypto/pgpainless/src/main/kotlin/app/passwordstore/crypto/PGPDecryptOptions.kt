/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

/** [CryptoOptions] implementation for PGPainless decrypt operations. */
public class PGPDecryptOptions private constructor(private val values: Map<String, Boolean>) :
  CryptoOptions {

  override fun isOptionEnabled(option: String): Boolean {
    return values.getOrDefault(option, false)
  }

  /** Implementation of a builder pattern for [PGPDecryptOptions]. */
  public class Builder {

    /** Build the final [PGPDecryptOptions] object. */
    public fun build(): PGPDecryptOptions {
      return PGPDecryptOptions(emptyMap())
    }
  }
}
