package com.zeapo.pwdstore

import android.net.Uri

import java.io.ByteArrayOutputStream
import java.io.UnsupportedEncodingException

/**
 * A single entry in password store.
 */
class PasswordEntry(private val content: String) {
    val password: String
    val username: String?
    val digits: String
    val totpSecret: String?
    val totpPeriod: Long
    val totpAlgorithm: String
    val hotpSecret: String?
    val hotpCounter: Long?
    var extraContent: String? = null
        private set
    private var isIncremented = false

    @Throws(UnsupportedEncodingException::class)
    constructor(os: ByteArrayOutputStream) : this(os.toString("UTF-8"))

    init {
        val passContent = content.split("\n".toRegex(), 2).toTypedArray()
        password = passContent[0]
        digits = findOtpDigits(content)
        totpSecret = findTotpSecret(content)
        totpPeriod = findTotpPeriod(content)
        totpAlgorithm = findTotpAlgorithm(content)
        hotpSecret = findHotpSecret(content)
        hotpCounter = findHotpCounter(content)
        extraContent = findExtraContent(passContent)
        username = findUsername()
    }

    fun hasExtraContent(): Boolean {
        return !extraContent.isNullOrEmpty()
    }

    fun hasUsername(): Boolean {
        return username != null
    }

    fun hasTotp(): Boolean {
        return totpSecret != null
    }

    fun hasHotp(): Boolean {
        return hotpSecret != null && hotpCounter != null
    }

    fun hotpIsIncremented(): Boolean {
        return isIncremented
    }

    fun incrementHotp() {
        content.split("\n".toRegex()).forEach { line ->
            if (line.startsWith("otpauth://hotp/")) {
                extraContent = extraContent?.replaceFirst("counter=[0-9]+".toRegex(), "counter=${hotpCounter!! + 1}")
                isIncremented = true
            }
        }
    }

    private fun findUsername(): String? {
        val extraLines = extraContent!!.split("\n".toRegex())
        for (line in extraLines) {
            for (field in USERNAME_FIELDS) {
                if (line.toLowerCase().startsWith("$field:")) {
                    return line.split(": *".toRegex(), 2).toTypedArray()[1]
                }
            }
        }
        return null
    }

    private fun findTotpSecret(decryptedContent: String): String? {
        decryptedContent.split("\n".toRegex()).forEach { line ->
            if (line.startsWith("otpauth://totp/")) {
                return Uri.parse(line).getQueryParameter("secret")
            }
        }
        return null
    }

    private fun findOtpDigits(decryptedContent: String): String {
        decryptedContent.split("\n".toRegex()).forEach { line ->
            if (line.startsWith("otpauth://totp/") ||
                    line.startsWith("otpauth://hotp/") &&
                    Uri.parse(line).getQueryParameter("digits") != null) {
                return Uri.parse(line).getQueryParameter("digits")!!
            }
        }
        return "6"
    }

    private fun findTotpPeriod(decryptedContent: String): Long {
        decryptedContent.split("\n".toRegex()).forEach { line ->
            if (line.startsWith("otpauth://totp/") &&
                    Uri.parse(line).getQueryParameter("period") != null) {
                return java.lang.Long.parseLong(Uri.parse(line).getQueryParameter("period")!!)
            }
        }
        return 30
    }

    private fun findTotpAlgorithm(decryptedContent: String): String {
        decryptedContent.split("\n".toRegex()).forEach { line ->
            if (line.startsWith("otpauth://totp/") &&
                    Uri.parse(line).getQueryParameter("algorithm") != null) {
                return Uri.parse(line).getQueryParameter("algorithm")!!
            }
        }
        return "sha1"
    }

    private fun findHotpSecret(decryptedContent: String): String? {
        decryptedContent.split("\n".toRegex()).forEach { line ->
            if (line.startsWith("otpauth://hotp/")) {
                return Uri.parse(line).getQueryParameter("secret")
            }
        }
        return null
    }

    private fun findHotpCounter(decryptedContent: String): Long? {
        decryptedContent.split("\n".toRegex()).forEach { line ->
            if (line.startsWith("otpauth://hotp/")) {
                return java.lang.Long.parseLong(Uri.parse(line).getQueryParameter("counter")!!)
            }
        }
        return null
    }

    private fun findExtraContent(passContent: Array<String>): String {
        val extraContent = if (passContent.size > 1) passContent[1] else ""
        // if there is a HOTP URI, we must return the extra content with the counter incremented
        return if (hasHotp()) {
            extraContent.replaceFirst("counter=[0-9]+".toRegex(), "counter=" + java.lang.Long.toString(hotpCounter!!))
        } else extraContent
    }

    companion object {

        private val USERNAME_FIELDS = arrayOf("login", "username")
    }
}
