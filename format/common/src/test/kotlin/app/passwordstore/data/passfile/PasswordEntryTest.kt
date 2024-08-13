/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.data.passfile

import app.cash.turbine.Event
import app.cash.turbine.test
import app.passwordstore.util.time.TestUserClock
import app.passwordstore.util.time.UserClock
import app.passwordstore.util.totp.UriTotpFinder
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class PasswordEntryTest {

  private val totpFinder = UriTotpFinder()

  private fun makeEntry(content: String, clock: UserClock = fakeClock) =
    PasswordEntry(clock, totpFinder, content.encodeToByteArray())

  @Test
  fun getPassword() {
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
      assertEquals("fooooo", makeEntry("\n${field.uppercase(Locale.getDefault())} fooooo").password)
      assertEquals("fooooo", makeEntry("GOPASS-SECRET-1.0\n$field fooooo").password)
      assertEquals("fooooo", makeEntry("someFirstLine\nUsername: bar\n$field fooooo").password)
    }
  }

  @Test
  fun getExtraContent() {
    assertEquals("bla\n", makeEntry("fooooo\nbla\n").extraContentString)
    assertEquals("bla", makeEntry("fooooo\nbla").extraContentString)
    assertEquals("", makeEntry("fooooo\n").extraContentString)
    assertEquals("", makeEntry("fooooo").extraContentString)
    assertEquals("blubb\n", makeEntry("\nblubb\n").extraContentString)
    assertEquals("blubb", makeEntry("\nblubb").extraContentString)
    assertEquals("blubb", makeEntry("blubb\npassword: foo").extraContentString)
    assertEquals("blubb", makeEntry("password: foo\nblubb").extraContentString)
    assertEquals(
      "blubb\nusername: bar",
      makeEntry("blubb\npassword: foo\nusername: bar").extraContentString,
    )
    assertEquals("", makeEntry("\n").extraContentString)
    assertEquals("", makeEntry("").extraContentString)
  }

  @Test
  fun parseExtraContentWithoutAuth() {
    var entry = makeEntry("username: abc\npassword: abc\ntest: abcdef")
    assertEquals(1, entry.extraContent.size)
    assertTrue(entry.extraContent.containsKey("test"))
    assertEquals("abcdef", entry.extraContent["test"])

    entry = makeEntry("username: abc\npassword: abc\ntest: :abcdef:")
    assertEquals(1, entry.extraContent.size)
    assertTrue(entry.extraContent.containsKey("test"))
    assertEquals(":abcdef:", entry.extraContent["test"])

    entry = makeEntry("username: abc\npassword: abc\ntest : ::abc:def::")
    assertEquals(1, entry.extraContent.size)
    assertTrue(entry.extraContent.containsKey("test"))
    assertEquals("::abc:def::", entry.extraContent["test"])

    entry = makeEntry("username: abc\npassword: abc\ntest: abcdef\ntest2: ghijkl")
    assertEquals(2, entry.extraContent.size)
    assertTrue(entry.extraContent.containsKey("test2"))
    assertEquals("ghijkl", entry.extraContent["test2"])

    entry = makeEntry("username: abc\npassword: abc\ntest: abcdef\n: ghijkl\n mnopqr:")
    assertEquals(2, entry.extraContent.size)
    assertTrue(entry.extraContent.containsKey("Extra Content"))
    assertEquals(": ghijkl\n mnopqr:", entry.extraContent["Extra Content"])

    entry = makeEntry("username: abc\npassword: abc\n:\n\n")
    assertEquals(1, entry.extraContent.size)
    assertTrue(entry.extraContent.containsKey("Extra Content"))
    assertEquals(":", entry.extraContent["Extra Content"])
  }

  @Test
  fun getUsername() {
    for (field in PasswordEntry.USERNAME_FIELDS) {
      assertEquals("username", makeEntry("\n$field username").username)
      assertEquals(
        "username",
        makeEntry("\n${field.uppercase(Locale.getDefault())} username").username,
      )
    }
    assertEquals("username", makeEntry("secret\nextra\nlogin: username\ncontent\n").username)
    assertEquals("username", makeEntry("\nextra\nusername: username\ncontent\n").username)
    assertEquals("username", makeEntry("\nUSERNaMe:  username\ncontent\n").username)
    assertEquals("username", makeEntry("\nlogin:    username").username)
    assertEquals("foo@example.com", makeEntry("\nemail: foo@example.com").username)
    assertEquals("username", makeEntry("\nidentity: username\nlogin: another_username").username)
    assertEquals("username", makeEntry("\nLOGiN:username").username)
    assertEquals("foo@example.com", makeEntry("pass\nmail:    foo@example.com").username)
    assertNull(makeEntry("secret\nextra\ncontent\n").username)
  }

  @Test
  fun hasUsername() {
    assertNotNull(makeEntry("secret\nextra\nlogin: username\ncontent\n").username)
    assertNull(makeEntry("secret\nextra\ncontent\n").username)
    assertNull(makeEntry("secret\nlogin failed\n").username)
    assertNull(makeEntry("\n").username)
    assertNull(makeEntry("").username)
  }

  @Test
  fun generatesOtpFromTotpUri() = runTest {
    val entry = makeEntry("secret\nextra\n$TOTP_URI")
    assertTrue(entry.hasTotp())
    entry.totp.test {
      val otp = expectMostRecentItem()
      assertEquals("818800", otp.value)
      assertEquals(30.seconds, otp.remainingTime)
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * Same as [generatesOtpFromTotpUri], but advances the clock by 5 seconds. This exercises the
   * [Totp.remainingTime] calculation logic, and acts as a regression test to resolve the bug which
   * blocked https://github.com/Android-Password-Store/Android-Password-Store/issues/1550.
   */
  @Test
  fun generatedOtpHasCorrectRemainingTime() = runTest {
    val entry = makeEntry("secret\nextra\n$TOTP_URI", TestUserClock.withAddedSeconds(5))
    assertTrue(entry.hasTotp())
    entry.totp.test {
      val otp = expectMostRecentItem()
      assertEquals("818800", otp.value)
      assertEquals(25.seconds, otp.remainingTime)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun generatesOtpWithOnlyUriInFile() = runTest {
    val entry = makeEntry(TOTP_URI)
    assertNull(entry.password)
    entry.totp.test {
      val otp = expectMostRecentItem()
      assertEquals("818800", otp.value)
      assertEquals(30.seconds, otp.remainingTime)
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun emitsTotpEverySecond() = runTest {
    val entry = makeEntry(TOTP_URI)
    entry.totp.test {
      delay(3000L)
      val events = cancelAndConsumeRemainingEvents()
      assertEquals(3, events.size)
      assertTrue { events.all { event -> event is Event.Item<Totp> } }
    }
  }

  // https://github.com/android-password-store/Android-Password-Store/issues/2949
  @Test
  fun disablesTotpForInvalidUri() = runTest {
    val entry = makeEntry("password\notpauth://totp/otp-secret?secret=")
    assertFalse(entry.hasTotp())
  }

  @Test
  fun onlyLooksForUriInFirstLine() {
    val entry = makeEntry("id:\n$TOTP_URI")
    assertNotNull(entry.password)
    assertTrue(entry.hasTotp())
    assertNull(entry.username)
  }

  // https://github.com/android-password-store/Android-Password-Store/issues/1190
  @Test
  fun extraContentWithMultipleUsernameFields() {
    val entry = makeEntry("pass\nuser: user\nid: id\n$TOTP_URI")
    assertTrue(entry.extraContent.isNotEmpty())
    assertTrue(entry.hasTotp())
    assertNotNull(entry.username)
    assertEquals("pass", entry.password)
    assertEquals("user", entry.username)
    assertEquals(mapOf("id" to "id"), entry.extraContent)
  }

  companion object {

    @Suppress("MaxLineLength")
    const val TOTP_URI =
      "otpauth://totp/ACME%20Co:john@example.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&algorithm=SHA1&digits=6&period=30"

    val fakeClock = TestUserClock()
  }
}
