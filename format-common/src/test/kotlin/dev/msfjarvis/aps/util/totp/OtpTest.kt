/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.totp

import com.github.michaelbull.result.get
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

internal class OtpTest {

  @Test
  fun testOtpGeneration6Digits() {
    assertEquals("953550", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333298159 / (1000 * 30), "SHA1", "6").get())
    assertEquals("275379", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333571918 / (1000 * 30), "SHA1", "6").get())
    assertEquals("867507", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333600517 / (1000 * 57), "SHA1", "6").get())
  }

  @Test
  fun testOtpGeneration10Digits() {
    assertEquals("0740900914", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333655044 / (1000 * 30), "SHA1", "10").get())
    assertEquals("0070632029", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333691405 / (1000 * 30), "SHA1", "10").get())
    assertEquals("1017265882", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333728893 / (1000 * 83), "SHA1", "10").get())
  }

  @Test
  fun testOtpGenerationIllegalInput() {
    assertNull(Otp.calculateCode("JBSWY3DPEHPK3PXP", 10000, "SHA0", "10").get())
    assertNull(Otp.calculateCode("JBSWY3DPEHPK3PXP", 10000, "SHA1", "a").get())
    assertNull(Otp.calculateCode("JBSWY3DPEHPK3PXP", 10000, "SHA1", "5").get())
    assertNull(Otp.calculateCode("JBSWY3DPEHPK3PXP", 10000, "SHA1", "11").get())
    assertNull(Otp.calculateCode("JBSWY3DPEHPK3PXPAAAAB", 10000, "SHA1", "6").get())
  }

  @Test
  fun testOtpGenerationUnusualSecrets() {
    assertEquals(
      "127764",
      Otp.calculateCode("JBSWY3DPEHPK3PXPAAAAAAAA", 1593367111963 / (1000 * 30), "SHA1", "6").get()
    )
    assertEquals("047515", Otp.calculateCode("JBSWY3DPEHPK3PXPAAAAA", 1593367171420 / (1000 * 30), "SHA1", "6").get())
  }

  @Test
  fun testOtpGenerationUnpaddedSecrets() {
    // Secret was generated with `echo 'string with some padding needed' | base32`
    // We don't care for the resultant OTP's actual value, we just want both the padded and
    // unpadded variant to generate the same one.
    val unpaddedOtp =
      Otp.calculateCode(
          "ON2HE2LOM4QHO2LUNAQHG33NMUQHAYLEMRUW4ZZANZSWKZDFMQFA",
          1593367171420 / (1000 * 30),
          "SHA1",
          "6"
        )
        .get()
    val paddedOtp =
      Otp.calculateCode(
          "ON2HE2LOM4QHO2LUNAQHG33NMUQHAYLEMRUW4ZZANZSWKZDFMQFA====",
          1593367171420 / (1000 * 30),
          "SHA1",
          "6"
        )
        .get()
    assertNotNull(unpaddedOtp)
    assertNotNull(paddedOtp)
    assertEquals(unpaddedOtp, paddedOtp)
  }
}
