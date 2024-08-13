/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.data.passfile

import androidx.annotation.VisibleForTesting
import app.passwordstore.util.time.UserClock
import app.passwordstore.util.totp.Otp
import app.passwordstore.util.totp.TotpFinder
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapBoth
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.collections.set
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/** Represents a single entry in the password store. */
public class PasswordEntry
@AssistedInject
constructor(
  /** A time source used to calculate the TOTP */
  private val clock: UserClock,
  /** [TotpFinder] implementation to extract data from a TOTP URI */
  private val totpFinder: TotpFinder,
  /** The content of this entry, as an array of bytes. */
  @Assisted bytes: ByteArray,
) {

  private val content = bytes.decodeToString()

  /** The password text for this entry. Can be null. */
  public val password: String?

  /** The username for this entry. Can be null. */
  public val username: String?

  /** A [String] to [String] [Map] of the extra content of this entry, in a key:value fashion. */
  public val extraContent: Map<String, String>

  /**
   * Direct [String] representation of the extra content of this entry, before any transforms are
   * applied. Only use this when the extra content is required in a formatting-preserving manner.
   */
  public val extraContentString: String

  /**
   * A [Flow] providing the current TOTP. It will start emitting only when collected. If this entry
   * does not have a TOTP secret, the flow will never emit. Users should call [hasTotp] before
   * collection to check if it is valid to collect this [Flow].
   */
  public val totp: Flow<Totp> = flow {
    require(totpSecret != null) { "Cannot collect this flow without a TOTP secret" }
    do {
      val otp = calculateTotp()
      if (otp.isOk) {
        emit(otp.value)
        delay(THOUSAND_MILLIS.milliseconds)
      } else {
        throw otp.error
      }
    } while (coroutineContext.isActive)
  }

  /** Obtain the [Totp.value] for this [PasswordEntry] at the current time. */
  public val currentOtp: String
    get() {
      val otp = calculateTotp()
      check(otp.isOk)
      return otp.value.value
    }

  /** String representation of [extraContent] but with usernames stripped. */
  public val extraContentWithoutUsername: String

  /**
   * String representation of [extraContent] but with authentication related data such as TOTP URIs
   * and usernames stripped.
   */
  public val extraContentWithoutAuthData: String
  private val totpSecret: String?

  init {
    val (foundPassword, passContent) = findAndStripPassword(content.split("\n".toRegex()))
    password = foundPassword
    extraContentString = passContent.joinToString("\n")
    extraContentWithoutUsername = generateExtraContentWithoutUsername()
    extraContentWithoutAuthData = generateExtraContentWithoutAuthData()
    extraContent = generateExtraContentPairs()
    username = findUsername()
    // Verify the TOTP secret is valid and disable TOTP if not.
    val secret = totpFinder.findSecret(content)
    totpSecret =
      if (secret != null && calculateTotp(secret).isOk) {
        secret
      } else {
        null
      }
  }

  public fun hasTotp(): Boolean {
    return totpSecret != null
  }

  @Suppress("ReturnCount")
  private fun findAndStripPassword(passContent: List<String>): Pair<String?, List<String>> {
    if (TotpFinder.TOTP_FIELDS.any { passContent[0].startsWith(it) }) return Pair(null, passContent)
    for (line in passContent) {
      for (prefix in PASSWORD_FIELDS) {
        if (line.startsWith(prefix, ignoreCase = true)) {
          return Pair(line.substring(prefix.length).trimStart(), passContent.minus(line))
        }
      }
    }
    return Pair(passContent[0], passContent.minus(passContent[0]))
  }

  private fun generateExtraContentWithoutUsername(): String {
    var foundUsername = false
    return extraContentString
      .lineSequence()
      .filter { line ->
        return@filter when {
          USERNAME_FIELDS.any { prefix -> line.startsWith(prefix, ignoreCase = true) } &&
            !foundUsername -> {
            foundUsername = true
            false
          }
          else -> {
            true
          }
        }
      }
      .joinToString(separator = "\n")
  }

  private fun generateExtraContentWithoutAuthData(): String {
    return generateExtraContentWithoutUsername()
      .lineSequence()
      .filter { line ->
        return@filter when {
          TotpFinder.TOTP_FIELDS.any { prefix -> line.startsWith(prefix, ignoreCase = true) } -> {
            false
          }
          else -> {
            true
          }
        }
      }
      .joinToString(separator = "\n")
  }

  private fun generateExtraContentPairs(): Map<String, String> {
    fun MutableMap<String, String>.putOrAppend(key: String, value: String) {
      if (value.isEmpty()) return
      val existing = this[key]
      this[key] =
        if (existing == null) {
          value
        } else {
          "$existing\n$value"
        }
    }

    val items = mutableMapOf<String, String>()
    extraContentWithoutAuthData.lines().forEach { line ->
      // Split the line on ':' and save all the parts into an array
      // "ABC : DEF:GHI" --> ["ABC", "DEF", "GHI"]
      val splitArray = line.split(":")
      // Take the first element of the array. This will be the key for the key-value pair.
      // ["ABC ", " DEF", "GHI"] -> key = "ABC"
      val key = splitArray.first().trimEnd()
      // Remove the first element from the array and join the rest of the string again with
      // ':' as separator.
      // ["ABC ", " DEF", "GHI"] -> value = "DEF:GHI"
      val value = splitArray.drop(1).joinToString(":").trimStart()

      if (key.isNotEmpty() && value.isNotEmpty()) {
        // If both key and value are not empty, we can form a pair with this so add it to
        // the map.
        // key = "ABC", value = "DEF:GHI"
        items[key] = value
      } else {
        // If either key or value is empty, we were not able to form proper key-value pair.
        // So append the original line into an "EXTRA CONTENT" map entry
        items.putOrAppend(EXTRA_CONTENT, line)
      }
    }

    return items
  }

  private fun findUsername(): String? {
    extraContentString.splitToSequence("\n").forEach { line ->
      for (prefix in USERNAME_FIELDS) {
        if (line.startsWith(prefix, ignoreCase = true))
          return line.substring(prefix.length).trimStart()
      }
    }
    return null
  }

  private fun calculateTotp(secret: String = totpSecret!!): Result<Totp, Throwable> {
    val digits = totpFinder.findDigits(content)
    val totpPeriod = totpFinder.findPeriod(content)
    val totpAlgorithm = totpFinder.findAlgorithm(content)
    val issuer = totpFinder.findIssuer(content)
    val millis = clock.millis()
    val remainingTime = (totpPeriod - ((millis / THOUSAND_MILLIS) % totpPeriod)).seconds
    return Otp.calculateCode(
        secret,
        millis / (THOUSAND_MILLIS * totpPeriod),
        totpAlgorithm,
        digits,
        issuer,
      )
      .mapBoth({ code -> Ok(Totp(code, remainingTime)) }, ::Err)
  }

  @AssistedFactory
  public interface Factory {
    public fun create(bytes: ByteArray): PasswordEntry
  }

  public companion object {

    private const val EXTRA_CONTENT = "Extra Content"
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public val USERNAME_FIELDS: Array<String> =
      arrayOf(
        "login:",
        "username:",
        "user:",
        "account:",
        "email:",
        "mail:",
        "name:",
        "handle:",
        "id:",
        "identity:",
      )
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public val PASSWORD_FIELDS: Array<String> = arrayOf("password:", "secret:", "pass:")
    private const val THOUSAND_MILLIS = 1000L
  }
}
