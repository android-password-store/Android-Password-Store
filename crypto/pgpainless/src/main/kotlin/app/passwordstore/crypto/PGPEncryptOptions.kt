/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

/** [CryptoOptions] implementation for PGPainless encrypt operations. */
public class PGPEncryptOptions private constructor(private val values: Map<String, Boolean>) :
  CryptoOptions {

  internal companion object {
    const val ASCII_ARMOR = "ASCII_ARMOR"
  }

  override fun isOptionEnabled(option: String): Boolean {
    return values.getOrDefault(option, false)
  }

  /** Implementation of a builder pattern for [PGPEncryptOptions]. */
  public class Builder {
    private val optionsMap = mutableMapOf<String, Boolean>()

    /**
     * Toggle whether the encryption operation output will be ASCII armored or in OpenPGP's binary
     * format.
     */
    public fun withAsciiArmor(enabled: Boolean): Builder {
      optionsMap[ASCII_ARMOR] = enabled
      return this
    }

    /** Build the final [PGPEncryptOptions] object. */
    public fun build(): PGPEncryptOptions {
      return PGPEncryptOptions(optionsMap)
    }
  }
}
