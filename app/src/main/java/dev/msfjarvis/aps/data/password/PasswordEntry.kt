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
    var extraContent: String
        private set

    @Throws(UnsupportedEncodingException::class)
    constructor(os: ByteArrayOutputStream) : this(os.toString("UTF-8"), UriTotpFinder())

    init {
        val (foundPassword, passContent) = findAndStripPassword(content.split("\n".toRegex()))
        password = foundPassword
        extraContent = passContent.joinToString("\n")
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

    val extraContentWithoutAuthData by lazy(LazyThreadSafetyMode.NONE) {
        var foundUsername = false
        extraContent.splitToSequence("\n").filter { line ->
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

    val extraContentWithoutAuthDataMap by lazy(LazyThreadSafetyMode.NONE) {
        val map = mutableMapOf<String, String>()
        extraContentWithoutAuthData.split("\n").forEach { item ->
            val splitArray = item.split(':')
            val key = splitArray.first().trimEnd()
            val value = (splitArray - splitArray.first()).joinToString(":").trimStart()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                // When both key and value are available
                map[key] = value
            } else {
                // If we cannot form a key-value pair, add the item as-it-is
                if (item.isNotEmpty()) {
                    map.putOrAppend("Extra Content", item)
                }
            }
        }
        map
    }

    private fun <K : Any> MutableMap<K, String>.putOrAppend(key: K, value: String) {
        if (!this.containsKey(key)) {
            this[key] = value
            return
        }
        val previousData = this[key]
        this[key] = previousData + "\n" + value
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
            "identity:"
        )

        val PASSWORD_FIELDS = arrayOf(
            "password:",
            "secret:",
            "pass:",
        )
    }
}
