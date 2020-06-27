/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.utils

import android.net.Uri

class UriTotpFinder : TotpFinder {
    override fun findSecret(content: String): String? {
        content.split("\n".toRegex()).forEach { line ->
            if (line.startsWith("otpauth://totp/")) {
                return Uri.parse(line).getQueryParameter("secret")
            }
            if (line.startsWith("totp:", ignoreCase = true)) {
                return line.split(": *".toRegex(), 2).toTypedArray()[1]
            }
        }
        return null
    }

    override fun findDigits(content: String): String {
        content.split("\n".toRegex()).forEach { line ->
            if ((line.startsWith("otpauth://totp/") ||
                    line.startsWith("otpauth://hotp/")) &&
                Uri.parse(line).getQueryParameter("digits") != null) {
                return Uri.parse(line).getQueryParameter("digits")!!
            }
        }
        return "6"
    }

    override fun findPeriod(content: String): Long {
        content.split("\n".toRegex()).forEach { line ->
            if (line.startsWith("otpauth://totp/") &&
                Uri.parse(line).getQueryParameter("period") != null) {
                return java.lang.Long.parseLong(Uri.parse(line).getQueryParameter("period")!!)
            }
        }
        return 30
    }

    override fun findAlgorithm(content: String): String {
        content.split("\n".toRegex()).forEach { line ->
            if (line.startsWith("otpauth://totp/") &&
                Uri.parse(line).getQueryParameter("algorithm") != null) {
                return Uri.parse(line).getQueryParameter("algorithm")!!
            }
        }
        return "sha1"
    }
}
