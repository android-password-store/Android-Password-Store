/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.model

import com.github.michaelbull.result.get
import com.zeapo.pwdstore.utils.Otp
import com.zeapo.pwdstore.utils.TotpFinder
import com.zeapo.pwdstore.utils.UriTotpFinder
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
        val passContent = content.split("\n".toRegex(), 2).toTypedArray()
        password = if (UriTotpFinder.TOTP_FIELDS.any { passContent[0].startsWith(it) }) "" else passContent[0]
        extraContent = findExtraContent(passContent)
        username = findUsername()
        digits = findOtpDigits(content)
        totpSecret = findTotpSecret(content)
        totpPeriod = findTotpPeriod(content)
        totpAlgorithm = findTotpAlgorithm(content)
    }

    fun hasExtraContent(): Boolean {
        return extraContent.isNotEmpty()
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
        extraContent.splitToSequence("\n").filter { line ->
            return@filter when {
                USERNAME_FIELDS.any { prefix -> line.startsWith(prefix, ignoreCase = true) } -> {
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

    private fun findUsername(): String? {
        extraContent.splitToSequence("\n").forEach { line ->
            for (prefix in USERNAME_FIELDS) {
                if (line.startsWith(prefix, ignoreCase = true))
                    return line.substring(prefix.length).trimStart()
            }
        }
        return null
    }

    private fun findExtraContent(passContent: Array<String>) = when {
        password.isEmpty() && passContent[0].isNotEmpty() -> passContent[0]
        passContent.size > 1 -> passContent[1]
        else -> ""
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
    }
}
