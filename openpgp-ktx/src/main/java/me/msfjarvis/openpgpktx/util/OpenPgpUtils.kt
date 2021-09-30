/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package me.msfjarvis.openpgpktx.util

import android.content.Context
import android.content.Intent
import java.io.Serializable
import java.util.Locale
import java.util.regex.Pattern

public object OpenPgpUtils {

  private val PGP_MESSAGE: Pattern =
    Pattern.compile(
      ".*?(-----BEGIN PGP MESSAGE-----.*?-----END PGP MESSAGE-----).*",
      Pattern.DOTALL
    )
  private val PGP_SIGNED_MESSAGE: Pattern =
    Pattern.compile(
      ".*?(-----BEGIN PGP SIGNED MESSAGE-----.*?-----BEGIN PGP SIGNATURE-----.*?-----END PGP SIGNATURE-----).*",
      Pattern.DOTALL
    )
  private val USER_ID_PATTERN = Pattern.compile("^(.*?)(?: \\((.*)\\))?(?: <(.*)>)?$")
  private val EMAIL_PATTERN = Pattern.compile("^<?\"?([^<>\"]*@[^<>\"]*[.]?[^<>\"]*)\"?>?$")
  public const val PARSE_RESULT_NO_PGP: Int = -1
  public const val PARSE_RESULT_MESSAGE: Int = 0
  public const val PARSE_RESULT_SIGNED_MESSAGE: Int = 1

  public fun parseMessage(message: String): Int {
    val matcherSigned = PGP_SIGNED_MESSAGE.matcher(message)
    val matcherMessage = PGP_MESSAGE.matcher(message)
    return when {
      matcherMessage.matches() -> PARSE_RESULT_MESSAGE
      matcherSigned.matches() -> PARSE_RESULT_SIGNED_MESSAGE
      else -> PARSE_RESULT_NO_PGP
    }
  }

  public fun isAvailable(context: Context): Boolean {
    val intent = Intent(OpenPgpApi.SERVICE_INTENT_2)
    val resInfo = context.packageManager.queryIntentServices(intent, 0)
    return resInfo.isNotEmpty()
  }

  public fun convertKeyIdToHex(keyId: Long): String {
    return "0x" + convertKeyIdToHex32bit(keyId shr 32) + convertKeyIdToHex32bit(keyId)
  }

  private fun convertKeyIdToHex32bit(keyId: Long): String {
    var hexString = java.lang.Long.toHexString(keyId and 0xffffffffL).lowercase(Locale.ENGLISH)
    while (hexString.length < 8) {
      hexString = "0$hexString"
    }
    return hexString
  }

  /**
   * Splits userId string into naming part, email part, and comment part. See SplitUserIdTest for
   * examples.
   */
  public fun splitUserId(userId: String): UserId {
    if (userId.isNotEmpty()) {
      val matcher = USER_ID_PATTERN.matcher(userId)
      if (matcher.matches()) {
        var name = if (matcher.group(1)?.isEmpty() == true) null else matcher.group(1)
        val comment = matcher.group(2)
        var email = matcher.group(3)
        if (email != null && name != null) {
          val emailMatcher = EMAIL_PATTERN.matcher(name)
          if (emailMatcher.matches() && email == emailMatcher.group(1)) {
            email = emailMatcher.group(1)
            name = null
          }
        }
        if (email == null && name != null) {
          val emailMatcher = EMAIL_PATTERN.matcher(name)
          if (emailMatcher.matches()) {
            email = emailMatcher.group(1)
            name = null
          }
        }
        return UserId(name, email, comment)
      }
    }
    return UserId(null, null, null)
  }

  /** Returns a composed user id. Returns null if name, email and comment are empty. */
  public fun createUserId(userId: UserId): String? {
    val userIdBuilder = StringBuilder()
    if (!userId.name.isNullOrEmpty()) {
      userIdBuilder.append(userId.name)
    }
    if (!userId.comment.isNullOrEmpty()) {
      userIdBuilder.append(" (")
      userIdBuilder.append(userId.comment)
      userIdBuilder.append(")")
    }
    if (!userId.email.isNullOrEmpty()) {
      userIdBuilder.append(" <")
      userIdBuilder.append(userId.email)
      userIdBuilder.append(">")
    }
    return if (userIdBuilder.isEmpty()) null else userIdBuilder.toString()
  }

  public class UserId(
    public val name: String?,
    public val email: String?,
    public val comment: String?
  ) : Serializable
}
