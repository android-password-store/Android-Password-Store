/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

/** Defines the contract for a grab-bag of options for individual cryptographic operations. */
public interface CryptoOptions {

  /** Returns a [Boolean] indicating if the [option] is enabled for this operation. */
  public fun isOptionEnabled(option: String): Boolean
}
