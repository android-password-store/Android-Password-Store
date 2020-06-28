package com.zeapo.pwdstore.utils

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OtpTest {

    @Test
    fun testOtpGeneration6Digits() {
        assertEquals("953550", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333298159 / (1000 * 30), "SHA1", "6"))
        assertEquals("275379", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333571918 / (1000 * 30), "SHA1", "6"))
        assertEquals("867507", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333600517 / (1000 * 57), "SHA1", "6"))
    }

    @Test
    fun testOtpGeneration10Digits() {
        assertEquals("0740900914", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333655044 / (1000 * 30), "SHA1", "10"))
        assertEquals("0070632029", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333691405 / (1000 * 30), "SHA1", "10"))
        assertEquals("1017265882", Otp.calculateCode("JBSWY3DPEHPK3PXP", 1593333728893 / (1000 * 83), "SHA1", "10"))
    }

    @Test
    fun testOtpGenerationIllegalInput() {
        assertNull(Otp.calculateCode("JBSWY3DPEHPK3PXP", 10000, "SHA0", "10"))
        assertNull(Otp.calculateCode("JBSWY3DPEHPK3PXP", 10000, "SHA1", "a"))
        assertNull(Otp.calculateCode("JBSWY3DPEHPK3PXP", 10000, "SHA1", "5"))
        assertNull(Otp.calculateCode("JBSWY3DPEHPK3PXP", 10000, "SHA1", "11"))
    }
}
