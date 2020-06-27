/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.utils

import org.junit.Test
import kotlin.test.assertEquals

class UriTotpFinderTest {

    private val totpFinder = UriTotpFinder()

    @Test
    fun findSecret() {
        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", totpFinder.findSecret(TOTP_URI))
    }

    @Test
    fun findDigits() {
        assertEquals("12", totpFinder.findDigits(TOTP_URI))
    }

    @Test
    fun findPeriod() {
        assertEquals(25, totpFinder.findPeriod(TOTP_URI))
    }

    @Test
    fun findAlgorithm() {
        assertEquals("SHA256", totpFinder.findAlgorithm(TOTP_URI))
    }

    companion object {
        const val TOTP_URI = "otpauth://totp/ACME%20Co:john@example.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&algorithm=SHA256&digits=12&period=25"
    }
}
