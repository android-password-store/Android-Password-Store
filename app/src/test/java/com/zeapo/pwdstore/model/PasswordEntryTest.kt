/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.model

import com.zeapo.pwdstore.utils.TotpFinder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordEntryTest {
    // This implementation just sends back defaults for now.
    private val testFinder = object : TotpFinder {
        override fun findSecret(content: String): String? {
            return ""
        }

        override fun findDigits(content: String): String {
            return "6"
        }

        override fun findPeriod(content: String): Long {
            return 30
        }

        override fun findAlgorithm(content: String): String {
            return "HmacSHA1"
        }
    }

    @Test fun testGetPassword() {
        assertEquals("fooooo", PasswordEntry("fooooo\nbla\n", testFinder).password)
        assertEquals("fooooo", PasswordEntry("fooooo\nbla", testFinder).password)
        assertEquals("fooooo", PasswordEntry("fooooo\n", testFinder).password)
        assertEquals("fooooo", PasswordEntry("fooooo", testFinder).password)
        assertEquals("", PasswordEntry("\nblubb\n", testFinder).password)
        assertEquals("", PasswordEntry("\nblubb", testFinder).password)
        assertEquals("", PasswordEntry("\n", testFinder).password)
        assertEquals("", PasswordEntry("", testFinder).password)
    }

    @Test fun testGetExtraContent() {
        assertEquals("bla\n", PasswordEntry("fooooo\nbla\n", testFinder).extraContent)
        assertEquals("bla", PasswordEntry("fooooo\nbla", testFinder).extraContent)
        assertEquals("", PasswordEntry("fooooo\n", testFinder).extraContent)
        assertEquals("", PasswordEntry("fooooo", testFinder).extraContent)
        assertEquals("blubb\n", PasswordEntry("\nblubb\n", testFinder).extraContent)
        assertEquals("blubb", PasswordEntry("\nblubb", testFinder).extraContent)
        assertEquals("", PasswordEntry("\n", testFinder).extraContent)
        assertEquals("", PasswordEntry("", testFinder).extraContent)
    }

    @Test fun testGetUsername() {
        for (field in PasswordEntry.USERNAME_FIELDS) {
            assertEquals("username", PasswordEntry("\n$field username", testFinder).username)
            assertEquals("username", PasswordEntry("\n${field.toUpperCase()} username", testFinder).username)
        }
        assertEquals(
            "username",
            PasswordEntry("secret\nextra\nlogin: username\ncontent\n", testFinder).username)
        assertEquals(
            "username",
            PasswordEntry("\nextra\nusername: username\ncontent\n", testFinder).username)
        assertEquals(
            "username", PasswordEntry("\nUSERNaMe:  username\ncontent\n", testFinder).username)
        assertEquals("username", PasswordEntry("\nlogin:    username", testFinder).username)
        assertEquals("foo@example.com", PasswordEntry("\nemail: foo@example.com", testFinder).username)
        assertEquals("username", PasswordEntry("\nidentity: username\nlogin: another_username", testFinder).username)
        assertEquals("username", PasswordEntry("\nLOGiN:username", testFinder).username)
        assertNull(PasswordEntry("secret\nextra\ncontent\n", testFinder).username)
    }

    @Test fun testHasUsername() {
        assertTrue(PasswordEntry("secret\nextra\nlogin: username\ncontent\n", testFinder).hasUsername())
        assertFalse(PasswordEntry("secret\nextra\ncontent\n", testFinder).hasUsername())
        assertFalse(PasswordEntry("secret\nlogin failed\n", testFinder).hasUsername())
        assertFalse(PasswordEntry("\n", testFinder).hasUsername())
        assertFalse(PasswordEntry("", testFinder).hasUsername())
    }
}
