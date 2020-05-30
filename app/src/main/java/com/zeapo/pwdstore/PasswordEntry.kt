/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import java.io.ByteArrayOutputStream
import java.io.UnsupportedEncodingException

/**
 * A single entry in password store.
 */
class PasswordEntry(content: String) {

    val password: String
    val username: String?
    var extraContent: String
        private set

    @Throws(UnsupportedEncodingException::class)
    constructor(os: ByteArrayOutputStream) : this(os.toString("UTF-8"))

    init {
        val passContent = content.split("\n".toRegex(), 2).toTypedArray()
        password = passContent[0]
        extraContent = findExtraContent(passContent)
        username = findUsername()
    }

    fun hasExtraContent(): Boolean {
        return extraContent.isNotEmpty()
    }

    fun hasUsername(): Boolean {
        return username != null
    }

    val extraContentWithoutUsername by lazy {
        var usernameFound = false
        extraContent.splitToSequence("\n").filter { line ->
            if (usernameFound)
                return@filter true
            if (USERNAME_FIELDS.any { prefix -> line.startsWith(prefix, ignoreCase = true) }) {
                usernameFound = true
                return@filter false
            }
            true
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

    private fun findExtraContent(passContent: Array<String>): String {
        return if (passContent.size > 1) passContent[1] else ""
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
