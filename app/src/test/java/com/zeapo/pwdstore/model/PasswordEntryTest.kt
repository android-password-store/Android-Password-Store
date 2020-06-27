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
    }

    @Test fun testGetExtraContent() {
        assertEquals("bla\n", makeEntry("fooooo\nbla\n").extraContent)
        assertEquals("bla", makeEntry("fooooo\nbla").extraContent)
        assertEquals("", makeEntry("fooooo\n").extraContent)
        assertEquals("", makeEntry("fooooo").extraContent)
        assertEquals("blubb\n", makeEntry("\nblubb\n").extraContent)
        assertEquals("blubb", makeEntry("\nblubb").extraContent)
        assertEquals("", makeEntry("\n").extraContent)
        assertEquals("", makeEntry("").extraContent)
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

    companion object {
        // This implementation just sends back defaults for now.
        val testFinder = object : TotpFinder {
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
    }
}
