/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.totp

import com.github.michaelbull.result.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OtpTest {

  private fun generateOtp(
    counter: Long,
    secret: String = "JBSWY3DPEHPK3PXP",
    algorithm: String = "SHA1",
    digits: String = "6",
    issuer: String? = null,
  ): String? {
    return Otp.calculateCode(secret, counter, algorithm, digits, issuer).get()
  }

  @Test
  fun otpGeneration6Digits() {
    assertEquals(
      "953550",
      generateOtp(
        counter = 1593333298159 / (1000 * 30),
      )
    )
    assertEquals(
      "275379",
      generateOtp(
        counter = 1593333571918 / (1000 * 30),
      )
    )
    assertEquals(
      "867507",
      generateOtp(
        counter = 1593333600517 / (1000 * 57),
      )
    )
  }

  @Test
  fun otpGeneration10Digits() {
    assertEquals(
      "0740900914",
      generateOtp(
        counter = 1593333655044 / (1000 * 30),
        digits = "10",
      )
    )
    assertEquals(
      "0070632029",
      generateOtp(
        counter = 1593333691405 / (1000 * 30),
        digits = "10",
      )
    )
    assertEquals(
      "1017265882",
      generateOtp(
        counter = 1593333728893 / (1000 * 83),
        digits = "10",
      )
    )
  }

  @Test
  fun otpGenerationIllegalInput() {
    assertNull(
      generateOtp(
        counter = 10000,
        algorithm = "SHA0",
        digits = "10",
      )
    )
    assertNull(
      generateOtp(
        counter = 10000,
        digits = "a",
      )
    )
    assertNull(
      generateOtp(
        counter = 10000,
        algorithm = "SHA1",
        digits = "5",
      )
    )
    assertNull(
      generateOtp(
        counter = 10000,
        digits = "11",
      )
    )
    assertNull(
      generateOtp(
        counter = 10000,
        secret = "JBSWY3DPEHPK3PXPAAAAB",
        digits = "6",
      )
    )
  }

  @Test
  fun otpGenerationUnusualSecrets() {
    assertEquals(
      "127764",
      generateOtp(
        counter = 1593367111963 / (1000 * 30),
        secret = "JBSWY3DPEHPK3PXPAAAAAAAA",
      )
    )
    assertEquals(
      "047515",
      generateOtp(
        counter = 1593367171420 / (1000 * 30),
        secret = "JBSWY3DPEHPK3PXPAAAAA",
      )
    )
  }

  @Test
  fun otpGenerationUnpaddedSecrets() {
    // Secret was generated with `echo 'string with some padding needed' | base32`
    // We don't care for the resultant OTP's actual value, we just want both the padded and
    // unpadded variant to generate the same one.
    val unpaddedOtp =
      generateOtp(
        counter = 1593367171420 / (1000 * 30),
        secret = "ON2HE2LOM4QHO2LUNAQHG33NMUQHAYLEMRUW4ZZANZSWKZDFMQFA",
      )
    val paddedOtp =
      generateOtp(
        counter = 1593367171420 / (1000 * 30),
        secret = "ON2HE2LOM4QHO2LUNAQHG33NMUQHAYLEMRUW4ZZANZSWKZDFMQFA====",
      )

    assertNotNull(unpaddedOtp)
    assertNotNull(paddedOtp)
    assertEquals(unpaddedOtp, paddedOtp)
  }

  @Test
  fun generateSteamTotp() {
    val issuerOtp =
      generateOtp(
        counter = 48297900 / (1000 * 30),
        secret = "STK7746GVMCHMNH5FBIAQXGPV3I7ZHRG",
        issuer = "Steam",
      )
    val digitsOtp =
      generateOtp(
        counter = 48297900 / (1000 * 30),
        secret = "STK7746GVMCHMNH5FBIAQXGPV3I7ZHRG",
        digits = "s",
      )
    assertNotNull(issuerOtp)
    assertNotNull(digitsOtp)
    assertEquals("6M3CT", issuerOtp)
    assertEquals("6M3CT", digitsOtp)
  }
}
