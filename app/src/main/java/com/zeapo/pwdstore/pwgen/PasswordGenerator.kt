/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.pwgen

import android.content.Context
import androidx.core.content.edit
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.clearFlag
import com.zeapo.pwdstore.utils.hasFlag

enum class PasswordOption(val key: String) {
    NoDigits("0"),
    NoUppercaseLetters("A"),
    NoAmbiguousCharacters("B"),
    FullyRandom("s"),
    AtLeastOneSymbol("y"),
    NoLowercaseLetters("L")
}

object PasswordGenerator {
    const val DEFAULT_LENGTH = 16

    const val DIGITS = 0x0001
    const val UPPERS = 0x0002
    const val SYMBOLS = 0x0004
    const val NO_AMBIGUOUS = 0x0008
    const val LOWERS = 0x0020

    const val DIGITS_STR = "0123456789"
    const val UPPERS_STR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOWERS_STR = "abcdefghijklmnopqrstuvwxyz"
    const val SYMBOLS_STR = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    const val AMBIGUOUS_STR = "B8G6I1l0OQDS5Z2"

    /**
     * Enables the [PasswordOption]s in [options] and sets [targetLength] as the length for
     * generated passwords.
     */
    fun setPrefs(ctx: Context, options: List<PasswordOption>, targetLength: Int): Boolean {
        ctx.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE).edit {
            for (possibleOption in PasswordOption.values())
                putBoolean(possibleOption.key, possibleOption in options)
            putInt("length", targetLength)
        }
        return true
    }

    fun isValidPassword(password: String, pwFlags: Int): Boolean {
        if (pwFlags hasFlag DIGITS && password.none { it in DIGITS_STR })
            return false
        if (pwFlags hasFlag UPPERS && password.none { it in UPPERS_STR })
            return false
        if (pwFlags hasFlag LOWERS && password.none { it in LOWERS_STR })
            return false
        if (pwFlags hasFlag SYMBOLS && password.none { it in SYMBOLS_STR })
            return false
        if (pwFlags hasFlag NO_AMBIGUOUS && password.any { it in AMBIGUOUS_STR })
            return false
        return true
    }

    /**
     * Generates a password using the preferences set by [setPrefs].
     */
    @Throws(PasswordGeneratorException::class)
    fun generate(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE)
        var numCharacterCategories = 0

        var phonemes = true
        var pwgenFlags = DIGITS or UPPERS or LOWERS

        for (option in PasswordOption.values()) {
            if (prefs.getBoolean(option.key, false)) {
                when (option) {
                    PasswordOption.NoDigits -> pwgenFlags = pwgenFlags.clearFlag(DIGITS)
                    PasswordOption.NoUppercaseLetters -> pwgenFlags = pwgenFlags.clearFlag(UPPERS)
                    PasswordOption.NoLowercaseLetters -> pwgenFlags = pwgenFlags.clearFlag(LOWERS)
                    PasswordOption.NoAmbiguousCharacters -> pwgenFlags = pwgenFlags or NO_AMBIGUOUS
                    PasswordOption.FullyRandom -> phonemes = false
                    PasswordOption.AtLeastOneSymbol -> {
                        numCharacterCategories++
                        pwgenFlags = pwgenFlags or SYMBOLS
                    }
                }
            } else {
                // The No* options are false, so the respective character category will be included.
                when (option) {
                    PasswordOption.NoDigits,
                    PasswordOption.NoUppercaseLetters,
                    PasswordOption.NoLowercaseLetters -> {
                        numCharacterCategories++
                    }
                    PasswordOption.NoAmbiguousCharacters,
                    PasswordOption.FullyRandom,
                        // Since AtLeastOneSymbol is not negated, it is counted in the if branch.
                    PasswordOption.AtLeastOneSymbol -> {
                    }
                }
            }
        }

        val length = prefs.getInt(PreferenceKeys.LENGTH, DEFAULT_LENGTH)
        if (pwgenFlags.clearFlag(NO_AMBIGUOUS) == 0) {
            throw PasswordGeneratorException(ctx.resources.getString(R.string.pwgen_no_chars_error))
        }
        if (length < numCharacterCategories) {
            throw PasswordGeneratorException(ctx.resources.getString(R.string.pwgen_length_too_short_error))
        }
        if (!(pwgenFlags hasFlag UPPERS) && !(pwgenFlags hasFlag LOWERS)) {
            phonemes = false
            pwgenFlags = pwgenFlags.clearFlag(NO_AMBIGUOUS)
        }
        // Experiments show that phonemes may require more than 1000 iterations to generate a valid
        // password if the length is not at least 6.
        if (length < 6) {
            phonemes = false
        }

        var password: String?
        var iterations = 0
        do {
            if (iterations++ > 1000)
                throw PasswordGeneratorException(ctx.resources.getString(R.string.pwgen_max_iterations_exceeded))
            password = if (phonemes) {
                RandomPhonemesGenerator.generate(length, pwgenFlags)
            } else {
                RandomPasswordGenerator.generate(length, pwgenFlags)
            }
        } while (password == null)
        return password
    }

    class PasswordGeneratorException(string: String) : Exception(string)
}
