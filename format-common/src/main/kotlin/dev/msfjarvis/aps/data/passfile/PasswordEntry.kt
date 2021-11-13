/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.data.passfile

import com.github.michaelbull.result.mapBoth
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dev.msfjarvis.aps.util.time.UserClock
import dev.msfjarvis.aps.util.totp.Otp
import dev.msfjarvis.aps.util.totp.TotpFinder
import kotlin.collections.set
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Represents a single entry in the password store. */
@OptIn(ExperimentalTime::class)
public class PasswordEntry
@AssistedInject
constructor(
  /** A time source used to calculate the TOTP */
  clock: UserClock,
  /** [TotpFinder] implementation to extract data from a TOTP URI */
  totpFinder: TotpFinder,
  /**
   * A cancellable [CoroutineScope] inside which we constantly emit new TOTP values as time elapses
   */
  @Assisted scope: CoroutineScope,
  /** The content of this entry, as an array of bytes. */
  @Assisted bytes: ByteArray,
) {

  private val _totp = MutableStateFlow("")
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
   * A [StateFlow] providing the current TOTP. It will emit a single empty string on initialization
   * which is replaced with a real TOTP if applicable. Call [hasTotp] to verify whether or not you
   * need to observe this value.
   */
  public val totp: StateFlow<String> = _totp.asStateFlow()

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
    extraContentWithoutAuthData = generateExtraContentWithoutAuthData()
    extraContent = generateExtraContentPairs()
    username = findUsername()
    totpSecret = totpFinder.findSecret(content)
    if (totpSecret != null) {
      scope.launch {
        val digits = totpFinder.findDigits(content)
        val totpPeriod = totpFinder.findPeriod(content)
        val totpAlgorithm = totpFinder.findAlgorithm(content)
        val issuer = totpFinder.findIssuer(content)
        val remainingTime = totpPeriod - (clock.millis() % totpPeriod)
        updateTotp(clock.millis(), totpPeriod, totpAlgorithm, digits, issuer)
        delay(Duration.seconds(remainingTime))
        repeat(Int.MAX_VALUE) {
          updateTotp(clock.millis(), totpPeriod, totpAlgorithm, digits, issuer)
          delay(Duration.seconds(totpPeriod))
        }
      }
    }
  }

  public fun hasTotp(): Boolean {
    return totpSecret != null
  }

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

  private fun generateExtraContentWithoutAuthData(): String {
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

  private fun updateTotp(
    millis: Long,
    totpPeriod: Long,
    totpAlgorithm: String,
    digits: String,
    issuer: String?,
  ) {
    if (totpSecret != null) {
      Otp.calculateCode(totpSecret, millis / (1000 * totpPeriod), totpAlgorithm, digits, issuer)
        .mapBoth({ code -> _totp.update { code } }, { throwable -> throw throwable })
    }
  }

  @AssistedFactory
  public interface Factory {
    public fun create(scope: CoroutineScope, bytes: ByteArray): PasswordEntry
  }

  internal companion object {

    private const val EXTRA_CONTENT = "Extra Content"
    internal val USERNAME_FIELDS =
      arrayOf(
        "login:",
        "username:",
        "user:",
        "account:",
        "email:",
        "name:",
        "handle:",
        "id:",
        "identity:",
      )
    internal val PASSWORD_FIELDS =
      arrayOf(
        "password:",
        "secret:",
        "pass:",
      )
  }
}
