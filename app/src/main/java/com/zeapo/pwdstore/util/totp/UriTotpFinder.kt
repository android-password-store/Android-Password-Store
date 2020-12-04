/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.util.totp

import android.net.Uri

/**
 * [Uri] backed TOTP URL parser.
 */
class UriTotpFinder : TotpFinder {

    override fun findSecret(content: String): String? {
        content.split("\n".toRegex()).forEach { line ->
            if (line.startsWith(TOTP_FIELDS[0])) {
                return Uri.parse(line).getQueryParameter("secret")
            }
            if (line.startsWith(TOTP_FIELDS[1], ignoreCase = true)) {
                return line.split(": *".toRegex(), 2).toTypedArray()[1]
            }
        }
        return null
    }

    override fun findDigits(content: String): String {
        content.split("\n".toRegex()).forEach { line ->
            if (line.startsWith(TOTP_FIELDS[0]) &&
                Uri.parse(line).getQueryParameter("digits") != null) {
                return Uri.parse(line).getQueryParameter("digits")!!
            }
        }
        return "6"
    }

    override fun findPeriod(content: String): Long {
        content.split("\n".toRegex()).forEach { line ->
            if (line.startsWith(TOTP_FIELDS[0]) &&
                Uri.parse(line).getQueryParameter("period") != null) {
                val period = Uri.parse(line).getQueryParameter("period")!!.toLongOrNull()
                if (period != null && period > 0)
                    return period
            }
        }
        return 30
    }

    override fun findAlgorithm(content: String): String {
        content.split("\n".toRegex()).forEach { line ->
            if (line.startsWith(TOTP_FIELDS[0]) &&
                Uri.parse(line).getQueryParameter("algorithm") != null) {
                return Uri.parse(line).getQueryParameter("algorithm")!!
            }
        }
        return "sha1"
    }

    companion object {

        val TOTP_FIELDS = arrayOf(
            "otpauth://totp",
            "totp:"
        )
    }
}
