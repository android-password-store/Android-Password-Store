/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.pwgen

import com.zeapo.pwdstore.utils.hasFlag

object RandomPasswordGenerator {

    /**
     * Generates a random password of length [targetLength], taking the following flags in [pwFlags]
     * into account, or fails to do so and returns null:
     *
     * - [PasswordGenerator.DIGITS]: If set, the password will contain at least one digit; if not
     *   set, the password will not contain any digits.
     * - [PasswordGenerator.UPPERS]: If set, the password will contain at least one uppercase
     *   letter; if not set, the password will not contain any uppercase letters.
     * - [PasswordGenerator.LOWERS]: If set, the password will contain at least one lowercase
     *   letter; if not set, the password will not contain any lowercase letters.
     * - [PasswordGenerator.SYMBOLS]: If set, the password will contain at least one symbol; if not
     *   set, the password will not contain any symbols.
     * - [PasswordGenerator.NO_AMBIGUOUS]: If set, the password will not contain any ambiguous
     *   characters.
     * - [PasswordGenerator.NO_VOWELS]: If set, the password will not contain any vowels.
     */
    fun generate(targetLength: Int, pwFlags: Int): String? {
        val bank = listOfNotNull(
            PasswordGenerator.DIGITS_STR.takeIf { pwFlags hasFlag PasswordGenerator.DIGITS },
            PasswordGenerator.UPPERS_STR.takeIf { pwFlags hasFlag PasswordGenerator.UPPERS },
            PasswordGenerator.LOWERS_STR.takeIf { pwFlags hasFlag PasswordGenerator.LOWERS },
            PasswordGenerator.SYMBOLS_STR.takeIf { pwFlags hasFlag PasswordGenerator.SYMBOLS }
        ).joinToString("")

        var password = ""
        while (password.length < targetLength) {
            val candidate = bank.secureRandomCharacter()
            if (pwFlags hasFlag PasswordGenerator.NO_AMBIGUOUS &&
                candidate in PasswordGenerator.AMBIGUOUS_STR) {
                continue
            }
            password += candidate
        }
        return password.takeIf { PasswordGenerator.isValidPassword(it, pwFlags) }
    }
}
