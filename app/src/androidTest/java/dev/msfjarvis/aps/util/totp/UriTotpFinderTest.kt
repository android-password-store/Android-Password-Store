/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.totp

import kotlin.test.assertEquals
import org.junit.Test

class UriTotpFinderTest {

  private val totpFinder = UriTotpFinder()

  @Test
  fun findSecret() {
    assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", totpFinder.findSecret(TOTP_URI))
    assertEquals(
      "HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ",
      totpFinder.findSecret("name\npassword\ntotp: HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ")
    )
    assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", totpFinder.findSecret(PASS_FILE_CONTENT))
  }

  @Test
  fun findDigits() {
    assertEquals("12", totpFinder.findDigits(TOTP_URI))
    assertEquals("12", totpFinder.findDigits(PASS_FILE_CONTENT))
  }

  @Test
  fun findPeriod() {
    assertEquals(25, totpFinder.findPeriod(TOTP_URI))
    assertEquals(25, totpFinder.findPeriod(PASS_FILE_CONTENT))
  }

  @Test
  fun findAlgorithm() {
    assertEquals("SHA256", totpFinder.findAlgorithm(TOTP_URI))
    assertEquals("SHA256", totpFinder.findAlgorithm(PASS_FILE_CONTENT))
  }

  companion object {

    const val TOTP_URI =
      "otpauth://totp/ACME%20Co:john@example.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&algorithm=SHA256&digits=12&period=25"
    const val PASS_FILE_CONTENT = "password\n$TOTP_URI"
  }
}
