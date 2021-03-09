/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.data.password

import androidx.annotation.VisibleForTesting
import com.github.michaelbull.result.get
import dev.msfjarvis.aps.util.totp.Otp
import dev.msfjarvis.aps.util.totp.TotpFinder
import dev.msfjarvis.aps.util.totp.UriTotpFinder
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * A single entry in password store. [totpFinder] is an implementation of [TotpFinder] that let's us
 * abstract out the Android-specific part and continue testing the class in the JVM.
 */
class PasswordEntry(content: String, private val totpFinder: TotpFinder = UriTotpFinder()) {

    val password: String
    val username: String?

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val digits: String

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val totpSecret: String?
    val totpPeriod: Long

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val totpAlgorithm: String
    val extraContent: String
    val extraContentWithoutAuthData: String
    val extraContentMap: Map<String, String>

    constructor(os: ByteArrayOutputStream) : this(os.toString(Charsets.UTF_8.name()), UriTotpFinder())

    init {
        val (foundPassword, passContent) = findAndStripPassword(content.split("\n".toRegex()))
        password = foundPassword
        extraContent = passContent.joinToString("\n")
        extraContentWithoutAuthData = generateExtraContentWithoutAuthData()
        extraContentMap = generateExtraContentPairs()
        username = findUsername()
        digits = findOtpDigits(content)
        totpSecret = findTotpSecret(content)
        totpPeriod = findTotpPeriod(content)
        totpAlgorithm = findTotpAlgorithm(content)
    }

    fun hasExtraContent(): Boolean {
        return extraContent.isNotEmpty()
    }

    fun hasExtraContentWithoutAuthData(): Boolean {
        return extraContentWithoutAuthData.isNotEmpty()
    }

    fun hasTotp(): Boolean {
        return totpSecret != null
    }

    fun hasUsername(): Boolean {
        return username != null
    }

    fun calculateTotpCode(): String? {
        if (totpSecret == null)
            return null
        return Otp.calculateCode(totpSecret, Date().time / (1000 * totpPeriod), totpAlgorithm, digits).get()
    }

    private fun generateExtraContentWithoutAuthData(): String {
        var foundUsername = false
        return extraContent
            .lineSequence()
            .filter { line ->
                return@filter when {
                    USERNAME_FIELDS.any { prefix -> line.startsWith(prefix, ignoreCase = true) } && !foundUsername -> {
                        foundUsername = true
                        false
                    }
                    line.startsWith("otpauth://", ignoreCase = true) ||
                        line.startsWith("totp:", ignoreCase = true) -> {
                        false
                    }
                    else -> {
                        true
                    }
                }
            }.joinToString(separator = "\n")
    }

    private fun generateExtraContentPairs(): Map<String, String> {
        fun MutableMap<String, String>.putOrAppend(key: String, value: String) {
            if (value.isEmpty()) return
            val existing = this[key]
            this[key] = if (existing == null) {
                value
            } else {
                "$existing\n$value"
            }
        }

        val items = mutableMapOf<String, String>()
        // Take extraContentWithoutAuthData and onEach line perform the following tasks
        extraContentWithoutAuthData.lines().forEach { line ->
            // Split the line on ':' and save all the parts into an array
            // "ABC : DEF:GHI" --> ["ABC", "DEF", "GHI"]
            val splitArray = line.split(":")
            // Take the first element of the array. This will be the key for the key-value pair.
            // ["ABC ", " DEF", "GHI"] -> key = "ABC"
            val key = splitArray.first().trimEnd()
            // Remove the first element from the array and join the rest of the string again with ':' as separator.
            // ["ABC ", " DEF", "GHI"] -> value = "DEF:GHI"
            val value = splitArray.drop(1).joinToString(":").trimStart()

            if (key.isNotEmpty() && value.isNotEmpty()) {
                // If both key and value are not empty, we can form a pair with this so add it to the map.
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
        extraContent.splitToSequence("\n").forEach { line ->
            for (prefix in USERNAME_FIELDS) {
                if (line.startsWith(prefix, ignoreCase = true))
                    return line.substring(prefix.length).trimStart()
            }
        }
        return null
    }

    private fun findAndStripPassword(passContent: List<String>): Pair<String, List<String>> {
        if (UriTotpFinder.TOTP_FIELDS.any { passContent[0].startsWith(it) }) return Pair("", passContent)
        for (line in passContent) {
            for (prefix in PASSWORD_FIELDS) {
                if (line.startsWith(prefix, ignoreCase = true)) {
                    return Pair(line.substring(prefix.length).trimStart(), passContent.minus(line))
                }
            }
        }
        return Pair(passContent[0], passContent.minus(passContent[0]))
    }

    private fun findTotpSecret(decryptedContent: String): String? {
        return totpFinder.findSecret(decryptedContent)
    }

    private fun findOtpDigits(decryptedContent: String): String {
        return totpFinder.findDigits(decryptedContent)
    }

    private fun findTotpPeriod(decryptedContent: String): Long {
        return totpFinder.findPeriod(decryptedContent)
    }

    private fun findTotpAlgorithm(decryptedContent: String): String {
        return totpFinder.findAlgorithm(decryptedContent)
    }

    companion object {

        private const val EXTRA_CONTENT = "Extra Content"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        val USERNAME_FIELDS = arrayOf(
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

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        val PASSWORD_FIELDS = arrayOf(
            "password:",
            "secret:",
            "pass:",
        )
    }
}
