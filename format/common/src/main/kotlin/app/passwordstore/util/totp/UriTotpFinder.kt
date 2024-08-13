/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.totp

import com.eygraber.uri.Uri
import javax.inject.Inject

/** [Uri] backed TOTP URL parser. */
public class UriTotpFinder @Inject constructor() : TotpFinder {

  private companion object {
    private const val DEFAULT_TOTP_PERIOD = 30L
  }

  override fun findSecret(content: String): String? {
    content.split("\n".toRegex()).forEach { line ->
      if (line.startsWith(TotpFinder.TOTP_FIELDS[0])) {
        return Uri.parse(line).getQueryParameter("secret")
      }
      if (line.startsWith(TotpFinder.TOTP_FIELDS[1], ignoreCase = true)) {
        return line.split(": *".toRegex(), 2).toTypedArray()[1]
      }
    }
    return null
  }

  override fun findDigits(content: String): String {
    return getQueryParameter(content, "digits") ?: "6"
  }

  override fun findPeriod(content: String): Long {
    return getQueryParameter(content, "period")?.toLongOrNull() ?: DEFAULT_TOTP_PERIOD
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
      if (
        line.startsWith(TotpFinder.TOTP_FIELDS[0]) && uri.getQueryParameter(parameterName) != null
      ) {
        return uri.getQueryParameter(parameterName)
      }
    }
    return null
  }
}
