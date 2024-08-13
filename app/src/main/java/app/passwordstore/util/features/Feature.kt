/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.features

/** List of all feature flags for the app. */
enum class Feature(
  /** Default value for the flag. */
  val defaultValue: Boolean,
  /** Key to retrieve the current value for the flag. */
  val configKey: String,
) {

  /** Opt into a cache layer for PGP passphrases. */
  EnablePGPPassphraseCache(false, "enable_gpg_passphrase_cache")
}
