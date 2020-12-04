/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.util.pwgen

import com.zeapo.pwdstore.util.extensions.hasFlag
import java.util.Locale

object RandomPhonemesGenerator {

    private const val CONSONANT = 0x0001
    private const val VOWEL = 0x0002
    private const val DIPHTHONG = 0x0004
    private const val NOT_FIRST = 0x0008

    private val elements = arrayOf(
        Element("a", VOWEL),
        Element("ae", VOWEL or DIPHTHONG),
        Element("ah", VOWEL or DIPHTHONG),
        Element("ai", VOWEL or DIPHTHONG),
        Element("b", CONSONANT),
        Element("c", CONSONANT),
        Element("ch", CONSONANT or DIPHTHONG),
        Element("d", CONSONANT),
        Element("e", VOWEL),
        Element("ee", VOWEL or DIPHTHONG),
        Element("ei", VOWEL or DIPHTHONG),
        Element("f", CONSONANT),
        Element("g", CONSONANT),
        Element("gh", CONSONANT or DIPHTHONG or NOT_FIRST),
        Element("h", CONSONANT),
        Element("i", VOWEL),
        Element("ie", VOWEL or DIPHTHONG),
        Element("j", CONSONANT),
        Element("k", CONSONANT),
        Element("l", CONSONANT),
        Element("m", CONSONANT),
        Element("n", CONSONANT),
        Element("ng", CONSONANT or DIPHTHONG or NOT_FIRST),
        Element("o", VOWEL),
        Element("oh", VOWEL or DIPHTHONG),
        Element("oo", VOWEL or DIPHTHONG),
        Element("p", CONSONANT),
        Element("ph", CONSONANT or DIPHTHONG),
        Element("qu", CONSONANT or DIPHTHONG),
        Element("r", CONSONANT),
        Element("s", CONSONANT),
        Element("sh", CONSONANT or DIPHTHONG),
        Element("t", CONSONANT),
        Element("th", CONSONANT or DIPHTHONG),
        Element("u", VOWEL),
        Element("v", CONSONANT),
        Element("w", CONSONANT),
        Element("x", CONSONANT),
        Element("y", CONSONANT),
        Element("z", CONSONANT)
    )

    private class Element(str: String, val flags: Int) {

        val upperCase = str.toUpperCase(Locale.ROOT)
        val lowerCase = str.toLowerCase(Locale.ROOT)
        val length = str.length
        val isAmbiguous = str.any { it in PasswordGenerator.AMBIGUOUS_STR }
    }

    /**
     * Generates a random human-readable password of length [targetLength], taking the following
     * flags in [pwFlags] into account, or fails to do so and returns null:
     *
     * - [PasswordGenerator.DIGITS]: If set, the password will contain at least one digit; if not
     *   set, the password will not contain any digits.
     * - [PasswordGenerator.UPPERS]: If set, the password will contain at least one uppercase
     *   letter; if not set, the password will not contain any uppercase letters.
     * - [PasswordGenerator.LOWERS]: If set, the password will contain at least one lowercase
     *   letter; if not set and [PasswordGenerator.UPPERS] is set, the password will not contain any
     *   lowercase characters; if both are not set, an exception is thrown.
     * - [PasswordGenerator.SYMBOLS]: If set, the password will contain at least one symbol; if not
     *   set, the password will not contain any symbols.
     * - [PasswordGenerator.NO_AMBIGUOUS]: If set, the password will not contain any ambiguous
     *   characters.
     */
    fun generate(targetLength: Int, pwFlags: Int): String? {
        require(pwFlags hasFlag PasswordGenerator.UPPERS || pwFlags hasFlag PasswordGenerator.LOWERS)

        var password = ""

        var isStartOfPart = true
        var nextBasicType = if (secureRandomBoolean()) VOWEL else CONSONANT
        var previousFlags = 0

        while (password.length < targetLength) {
            // First part: Add a single letter or pronounceable pair of letters in varying case.

            val candidate = elements.secureRandomElement()

            // Reroll if the candidate does not fulfill the current requirements.
            if (!candidate.flags.hasFlag(nextBasicType) ||
                (isStartOfPart && candidate.flags hasFlag NOT_FIRST) ||
                // Don't let a diphthong that starts with a vowel follow a vowel.
                (previousFlags hasFlag VOWEL && candidate.flags hasFlag VOWEL && candidate.flags hasFlag DIPHTHONG) ||
                // Don't add multi-character candidates if we would go over the targetLength.
                (password.length + candidate.length > targetLength) ||
                (pwFlags hasFlag PasswordGenerator.NO_AMBIGUOUS && candidate.isAmbiguous)) {
                continue
            }

            // At this point the candidate could be appended to the password, but we still have
            // to determine the case. If no upper case characters are required, we don't add
            // any.
            val useUpperIfBothCasesAllowed =
                (isStartOfPart || candidate.flags hasFlag CONSONANT) && secureRandomBiasedBoolean(20)
            password += if (pwFlags hasFlag PasswordGenerator.UPPERS &&
                (!(pwFlags hasFlag PasswordGenerator.LOWERS) || useUpperIfBothCasesAllowed)) {
                candidate.upperCase
            } else {
                candidate.lowerCase
            }

            // We ensured above that we will not go above the target length.
            check(password.length <= targetLength)
            if (password.length == targetLength)
                break

            // Second part: Add digits and symbols with a certain probability (if requested) if
            // they would not directly follow the first character in a pronounceable part.

            if (!isStartOfPart && pwFlags hasFlag PasswordGenerator.DIGITS &&
                secureRandomBiasedBoolean(30)) {
                var randomDigit: Char
                do {
                    randomDigit = secureRandomNumber(10).toString(10).first()
                } while (pwFlags hasFlag PasswordGenerator.NO_AMBIGUOUS &&
                    randomDigit in PasswordGenerator.AMBIGUOUS_STR)

                password += randomDigit
                // Begin a new pronounceable part after every digit.
                isStartOfPart = true
                nextBasicType = if (secureRandomBoolean()) VOWEL else CONSONANT
                previousFlags = 0
                continue
            }

            if (!isStartOfPart && pwFlags hasFlag PasswordGenerator.SYMBOLS &&
                secureRandomBiasedBoolean(20)) {
                var randomSymbol: Char
                do {
                    randomSymbol = PasswordGenerator.SYMBOLS_STR.secureRandomCharacter()
                } while (pwFlags hasFlag PasswordGenerator.NO_AMBIGUOUS &&
                    randomSymbol in PasswordGenerator.AMBIGUOUS_STR)
                password += randomSymbol
                // Continue the password generation as if nothing was added.
            }

            // Third part: Determine the basic type of the next character depending on the letter
            // we just added.
            nextBasicType = when {
                candidate.flags.hasFlag(CONSONANT) -> VOWEL
                previousFlags.hasFlag(VOWEL) || candidate.flags.hasFlag(DIPHTHONG) ||
                    secureRandomBiasedBoolean(60) -> CONSONANT
                else -> VOWEL
            }
            previousFlags = candidate.flags
            isStartOfPart = false
        }
        return password.takeIf { PasswordGenerator.isValidPassword(it, pwFlags) }
    }
}
