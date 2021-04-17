/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.totp

/** Defines a class that can extract relevant parts of a TOTP URL for use by the app. */
public interface TotpFinder {

  /** Get the TOTP secret from the given extra content. */
  public fun findSecret(content: String): String?

  /** Get the number of digits required in the final OTP. */
  public fun findDigits(content: String): String

  /** Get the TOTP timeout period. */
  public fun findPeriod(content: String): Long

  /** Get the algorithm for the TOTP secret. */
  public fun findAlgorithm(content: String): String

  public companion object {
    public val TOTP_FIELDS: Array<String> = arrayOf("otpauth://totp", "totp:")
  }
}
