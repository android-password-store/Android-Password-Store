/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class PasswordEntryTest {
    @Test fun testGetPassword() {
        assertEquals("fooooo", PasswordEntry("fooooo\nbla\n").password)
        assertEquals("fooooo", PasswordEntry("fooooo\nbla").password)
        assertEquals("fooooo", PasswordEntry("fooooo\n").password)
        assertEquals("fooooo", PasswordEntry("fooooo").password)
        assertEquals("", PasswordEntry("\nblubb\n").password)
        assertEquals("", PasswordEntry("\nblubb").password)
        assertEquals("", PasswordEntry("\n").password)
        assertEquals("", PasswordEntry("").password)
    }

    @Test fun testGetExtraContent() {
        assertEquals("bla\n", PasswordEntry("fooooo\nbla\n").extraContent)
        assertEquals("bla", PasswordEntry("fooooo\nbla").extraContent)
        assertEquals("", PasswordEntry("fooooo\n").extraContent)
        assertEquals("", PasswordEntry("fooooo").extraContent)
        assertEquals("blubb\n", PasswordEntry("\nblubb\n").extraContent)
        assertEquals("blubb", PasswordEntry("\nblubb").extraContent)
        assertEquals("", PasswordEntry("\n").extraContent)
        assertEquals("", PasswordEntry("").extraContent)
    }

    @Test fun testGetUsername() {
        for (field in PasswordEntry.USERNAME_FIELDS) {
            assertEquals("username", PasswordEntry("\n$field username").username)
            assertEquals("username", PasswordEntry("\n${field.toUpperCase()} username").username)
        }
        assertEquals(
                "username",
                PasswordEntry("secret\nextra\nlogin: username\ncontent\n").username)
        assertEquals(
                "username",
                PasswordEntry("\nextra\nusername: username\ncontent\n").username)
        assertEquals(
                "username", PasswordEntry("\nUSERNaMe:  username\ncontent\n").username)
        assertEquals("username", PasswordEntry("\nlogin:    username").username)
        assertEquals("foo@example.com", PasswordEntry("\nemail: foo@example.com").username)
        assertEquals("username", PasswordEntry("\nidentity: username\nlogin: another_username").username)
        assertEquals("username", PasswordEntry("\nLOGiN:username").username)
        assertNull(PasswordEntry("secret\nextra\ncontent\n").username)
    }

    @Test fun testHasUsername() {
        assertTrue(PasswordEntry("secret\nextra\nlogin: username\ncontent\n").hasUsername())
        assertFalse(PasswordEntry("secret\nextra\ncontent\n").hasUsername())
        assertFalse(PasswordEntry("secret\nlogin failed\n").hasUsername())
        assertFalse(PasswordEntry("\n").hasUsername())
        assertFalse(PasswordEntry("").hasUsername())
    }

    @Test fun testNoTotpUriPresent() {
        val entry = PasswordEntry("secret\nextra\nlogin: username\ncontent")
        assertFalse(entry.hasTotp())
        assertNull(entry.totpSecret)
    }

    @Test fun testTotpUriInPassword() {
        val entry = PasswordEntry("otpauth://totp/test?secret=JBSWY3DPEHPK3PXP")
        assertTrue(entry.hasTotp())
        assertEquals("JBSWY3DPEHPK3PXP", entry.totpSecret)
    }

    @Test fun testTotpUriInContent() {
        val entry = PasswordEntry(
                "secret\nusername: test\notpauth://totp/test?secret=JBSWY3DPEHPK3PXP")
        assertTrue(entry.hasTotp())
        assertEquals("JBSWY3DPEHPK3PXP", entry.totpSecret)
    }

    @Test fun testNoHotpUriPresent() {
        val entry = PasswordEntry("secret\nextra\nlogin: username\ncontent")
        assertFalse(entry.hasHotp())
        assertNull(entry.hotpSecret)
        assertNull(entry.hotpCounter)
    }

    @Test fun testHotpUriInPassword() {
        val entry = PasswordEntry("otpauth://hotp/test?secret=JBSWY3DPEHPK3PXP&counter=25")
        assertTrue(entry.hasHotp())
        assertEquals("JBSWY3DPEHPK3PXP", entry.hotpSecret)
        assertEquals(25, entry.hotpCounter)
    }

    @Test fun testHotpUriInContent() {
        val entry = PasswordEntry(
                "secret\nusername: test\notpauth://hotp/test?secret=JBSWY3DPEHPK3PXP&counter=25")
        assertTrue(entry.hasHotp())
        assertEquals("JBSWY3DPEHPK3PXP", entry.hotpSecret)
        assertEquals(25, entry.hotpCounter)
    }
}
