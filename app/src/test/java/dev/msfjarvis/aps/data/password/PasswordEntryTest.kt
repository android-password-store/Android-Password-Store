/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.data.password

import com.github.michaelbull.result.get
import dev.msfjarvis.aps.util.totp.Otp
import dev.msfjarvis.aps.util.totp.TotpFinder
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PasswordEntryTest {

    private fun makeEntry(content: String) = PasswordEntry(content, testFinder)

    @Test fun testGetPassword() {
        assertEquals("fooooo", makeEntry("fooooo\nbla\n").password)
        assertEquals("fooooo", makeEntry("fooooo\nbla").password)
        assertEquals("fooooo", makeEntry("fooooo\n").password)
        assertEquals("fooooo", makeEntry("fooooo").password)
        assertEquals("", makeEntry("\nblubb\n").password)
        assertEquals("", makeEntry("\nblubb").password)
        assertEquals("", makeEntry("\n").password)
        assertEquals("", makeEntry("").password)
        for (field in PasswordEntry.PASSWORD_FIELDS) {
            assertEquals("fooooo", makeEntry("\n$field fooooo").password)
            assertEquals("fooooo", makeEntry("\n${field.toUpperCase()} fooooo").password)
            assertEquals("fooooo", makeEntry("GOPASS-SECRET-1.0\n$field fooooo").password)
            assertEquals("fooooo", makeEntry("someFirstLine\nUsername: bar\n$field fooooo").password)
        }
    }

    @Test fun testGetExtraContent() {
        assertEquals("bla\n", makeEntry("fooooo\nbla\n").extraContent)
        assertEquals("bla", makeEntry("fooooo\nbla").extraContent)
        assertEquals("", makeEntry("fooooo\n").extraContent)
        assertEquals("", makeEntry("fooooo").extraContent)
        assertEquals("blubb\n", makeEntry("\nblubb\n").extraContent)
        assertEquals("blubb", makeEntry("\nblubb").extraContent)
        assertEquals("blubb", makeEntry("blubb\npassword: foo").extraContent)
        assertEquals("blubb", makeEntry("password: foo\nblubb").extraContent)
        assertEquals("blubb\nusername: bar", makeEntry("blubb\npassword: foo\nusername: bar").extraContent)
        assertEquals("", makeEntry("\n").extraContent)
        assertEquals("", makeEntry("").extraContent)
    }

    @Test fun parseExtraContentWithoutAuth() {
        var entry = makeEntry("username: abc\npassword: abc\ntest: abcdef")
        assertEquals(1, entry.extraContentMap.size)
        assertTrue(entry.extraContentMap.containsKey("test"))
        assertEquals("abcdef", entry.extraContentMap["test"])

        entry = makeEntry("username: abc\npassword: abc\ntest: :abcdef:")
        assertEquals(1, entry.extraContentMap.size)
        assertTrue(entry.extraContentMap.containsKey("test"))
        assertEquals(":abcdef:", entry.extraContentMap["test"])

        entry = makeEntry("username: abc\npassword: abc\ntest : ::abc:def::")
        assertEquals(1, entry.extraContentMap.size)
        assertTrue(entry.extraContentMap.containsKey("test"))
        assertEquals("::abc:def::", entry.extraContentMap["test"])

        entry = makeEntry("username: abc\npassword: abc\ntest: abcdef\ntest2: ghijkl")
        assertEquals(2, entry.extraContentMap.size)
        assertTrue(entry.extraContentMap.containsKey("test2"))
        assertEquals("ghijkl", entry.extraContentMap["test2"])

        entry = makeEntry("username: abc\npassword: abc\ntest: abcdef\n: ghijkl\n mnopqr:")
        assertEquals(1, entry.extraContentMap.size)
        /*
        Whether or not the catch-all 'Extra Content' field will be a thing is up for debate
        assertEquals(2, entry.extraContentMap.size)
        assertTrue(entry.extraContentMap.containsKey("Extra Content"))
        assertEquals(": ghijkl\n mnopqr:", entry.extraContentMap["Extra Content"])
        */
    }

    @Test fun testGetUsername() {
        for (field in PasswordEntry.USERNAME_FIELDS) {
            assertEquals("username", makeEntry("\n$field username").username)
            assertEquals("username", makeEntry("\n${field.toUpperCase()} username").username)
        }
        assertEquals(
            "username",
            makeEntry("secret\nextra\nlogin: username\ncontent\n").username)
        assertEquals(
            "username",
            makeEntry("\nextra\nusername: username\ncontent\n").username)
        assertEquals(
            "username", makeEntry("\nUSERNaMe:  username\ncontent\n").username)
        assertEquals("username", makeEntry("\nlogin:    username").username)
        assertEquals("foo@example.com", makeEntry("\nemail: foo@example.com").username)
        assertEquals("username", makeEntry("\nidentity: username\nlogin: another_username").username)
        assertEquals("username", makeEntry("\nLOGiN:username").username)
        assertNull(makeEntry("secret\nextra\ncontent\n").username)
    }

    @Test fun testHasUsername() {
        assertTrue(makeEntry("secret\nextra\nlogin: username\ncontent\n").hasUsername())
        assertFalse(makeEntry("secret\nextra\ncontent\n").hasUsername())
        assertFalse(makeEntry("secret\nlogin failed\n").hasUsername())
        assertFalse(makeEntry("\n").hasUsername())
        assertFalse(makeEntry("").hasUsername())
    }

    @Test fun testGeneratesOtpFromTotpUri() {
        val entry = makeEntry("secret\nextra\n$TOTP_URI")
        assertTrue(entry.hasTotp())
        val code = Otp.calculateCode(
            entry.totpSecret!!,
            // The hardcoded date value allows this test to stay reproducible.
            Date(8640000).time / (1000 * entry.totpPeriod),
            entry.totpAlgorithm,
            entry.digits
        ).get()
        assertNotNull(code) { "Generated OTP cannot be null" }
        assertEquals(entry.digits.toInt(), code.length)
        assertEquals("545293", code)
    }

    @Test fun testGeneratesOtpWithOnlyUriInFile() {
        val entry = makeEntry(TOTP_URI)
        assertTrue(entry.password.isEmpty())
        assertTrue(entry.hasTotp())
        val code = Otp.calculateCode(
            entry.totpSecret!!,
            // The hardcoded date value allows this test to stay reproducible.
            Date(8640000).time / (1000 * entry.totpPeriod),
            entry.totpAlgorithm,
            entry.digits
        ).get()
        assertNotNull(code) { "Generated OTP cannot be null" }
        assertEquals(entry.digits.toInt(), code.length)
        assertEquals("545293", code)
    }

    @Test fun testOnlyLooksForUriInFirstLine() {
        val entry = makeEntry("id:\n$TOTP_URI")
        assertTrue(entry.password.isNotEmpty())
        assertTrue(entry.hasTotp())
        assertFalse(entry.hasUsername())
    }

    // https://github.com/android-password-store/Android-Password-Store/issues/1190
    @Test fun extraContentWithMultipleUsernameFields() {
        val entry = makeEntry("pass\nuser: user\nid: id\n$TOTP_URI")
        assertTrue(entry.hasExtraContent())
        assertTrue(entry.hasTotp())
        assertTrue(entry.hasUsername())
        assertEquals("pass", entry.password)
        assertEquals("user", entry.username)
        assertEquals("id: id", entry.extraContentWithoutAuthData)
    }

    companion object {

        const val TOTP_URI = "otpauth://totp/ACME%20Co:john@example.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&algorithm=SHA1&digits=6&period=30"

        // This implementation is hardcoded for the URI above.
        val testFinder = object : TotpFinder {
            override fun findSecret(content: String): String? {
                return "HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ"
            }

            override fun findDigits(content: String): String {
                return "6"
            }

            override fun findPeriod(content: String): Long {
                return 30
            }

            override fun findAlgorithm(content: String): String {
                return "SHA1"
            }
        }
    }
}
