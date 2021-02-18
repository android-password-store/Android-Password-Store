/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.data.password

import com.github.michaelbull.result.get
import dev.msfjarvis.aps.util.totp.Otp
import dev.msfjarvis.aps.util.totp.TotpFinder
import dev.msfjarvis.aps.util.totp.UriTotpFinder
import java.io.ByteArrayOutputStream
import java.io.UnsupportedEncodingException
import java.util.Date

/**
 * A single entry in password store. [totpFinder] is an implementation of [TotpFinder] that let's us
 * abstract out the Android-specific part and continue testing the class in the JVM.
 */
class PasswordEntry(content: String, private val totpFinder: TotpFinder = UriTotpFinder()) {

    val password: String
    val username: String?
    val digits: String
    val totpSecret: String?
    val totpPeriod: Long
    val totpAlgorithm: String
    val extraContent: String
    val extraContentWithoutAuthData: String
    val extraContentMap: Map<String, String>

    @Throws(UnsupportedEncodingException::class)
    constructor(os: ByteArrayOutputStream) : this(os.toString("UTF-8"), UriTotpFinder())

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
        val items = mutableMapOf<String, String>()
        extraContentWithoutAuthData
            .lines()
            // Split on ':'
            .map { line -> line.split(':') }
            // Only take the item when a pair can be formed
            .filter { list -> list.size >= 2 }
            // Convert them to a Pair so it's easier to perform checks
            // Since the actual values can also contain colons,
            // we join them back and trim any initial spaces in them,
            // and trailing spaces in the keys.
            .map { list -> list[0].trimEnd() to list.drop(1).joinToString(":").trimStart() }
            // Ensure neither key nor value are empty
            .filter { pair -> pair.first.isNotBlank() && pair.second.isNotBlank() }
            // Write the validated contents into the map
            .map { pair -> items[pair.first] = pair.second }

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
        val PASSWORD_FIELDS = arrayOf(
            "password:",
            "secret:",
            "pass:",
        )
    }
}
