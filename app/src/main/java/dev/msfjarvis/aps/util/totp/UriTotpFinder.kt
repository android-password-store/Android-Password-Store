/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.totp

import android.net.Uri
import javax.inject.Inject

/** [Uri] backed TOTP URL parser. */
class UriTotpFinder @Inject constructor() : TotpFinder {

  override fun findSecret(content: String): String? {
    content.split("\n".toRegex()).forEach { line ->
      if (line.startsWith(TOTP_FIELDS[0])) {
        return Uri.parse(line).getQueryParameter("secret")
      }
      if (line.startsWith(TOTP_FIELDS[1], ignoreCase = true)) {
        return line.split(": *".toRegex(), 2).toTypedArray()[1]
      }
    }
    return null
  }

  override fun findDigits(content: String): String {
    return getQueryParameter(content, "digits") ?: "6"
  }

  override fun findPeriod(content: String): Long {
    return getQueryParameter(content, "period")?.toLongOrNull() ?: 30
  }

  override fun findAlgorithm(content: String): String {
    return getQueryParameter(content, "algorithm") ?: "sha1"
  }

  override fun findIssuer(content: String): String? {
    return getQueryParameter(content, "issuer") ?: Uri.parse(content).authority
  }

  private fun getQueryParameter(content: String, parameterName: String): String? {
    content.split("\n".toRegex()).forEach { line ->
      val uri = Uri.parse(line)
      if (line.startsWith(TOTP_FIELDS[0]) && uri.getQueryParameter(parameterName) != null) {
        return uri.getQueryParameter(parameterName)
      }
    }
    return null
  }

  companion object {

    val TOTP_FIELDS = arrayOf("otpauth://totp", "totp:")
  }
}
