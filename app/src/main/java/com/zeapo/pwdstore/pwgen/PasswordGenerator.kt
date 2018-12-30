package com.zeapo.pwdstore.pwgen

import android.content.Context
import android.content.SharedPreferences

import java.util.ArrayList

object PasswordGenerator {
    internal val DIGITS = 0x0001
    internal val UPPERS = 0x0002
    internal val SYMBOLS = 0x0004
    internal val AMBIGUOUS = 0x0008
    internal val NO_VOWELS = 0x0010

    internal val DIGITS_STR = "0123456789"
    internal val UPPERS_STR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    internal val LOWERS_STR = "abcdefghijklmnopqrstuvwxyz"
    internal val SYMBOLS_STR = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    internal val AMBIGUOUS_STR = "B8G6I1l0OQDS5Z2"
    internal val VOWELS_STR = "01aeiouyAEIOUY"

    // No a, c, n, h, H, C, 1, N
    private val pwOptions = "0ABsvy"

    /**
     * Sets password generation preferences.
     *
     * @param ctx     context from which to retrieve SharedPreferences from
     * preferences file 'PasswordGenerator'
     * @param argv    options for password generation
     * <table summary="options for password generation">
     * <tr><td>Option</td><td>Description</td></tr>
     * <tr><td>0</td><td>don't include numbers</td></tr>
     * <tr><td>A</td><td>don't include uppercase letters</td></tr>
     * <tr><td>B</td><td>don't include ambiguous charactersl</td></tr>
     * <tr><td>s</td><td>generate completely random passwords</td></tr>
     * <tr><td>v</td><td>don't include vowels</td></tr>
     * <tr><td>y</td><td>include at least one symbol</td></tr>
    </table> *
     * @param numArgv numerical options for password generation: length of
     * generated passwords followed by number of passwords to
     * generate
     * @return `false` if a numerical options is invalid,
     * `true` otherwise
     */
    fun setPrefs(ctx: Context, argv: ArrayList<String>, vararg numArgv: Int): Boolean {
        val prefs = ctx.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        for (option in pwOptions.toCharArray()) {
            if (argv.contains(option.toString())) {
                editor.putBoolean(option.toString(), true)
                argv.remove(option.toString())
            } else {
                editor.putBoolean(option.toString(), false)
            }
        }
        var i = 0
        while (i < numArgv.size && i < 2) {
            if (numArgv[i] <= 0) {
                // Invalid password length or number of passwords
                return false
            }
            val name = if (i == 0) "length" else "num"
            editor.putInt(name, numArgv[i])
            i++
        }
        editor.apply()
        return true
    }

    /**
     * Generates passwords using the preferences set by
     * [.setPrefs].
     *
     * @param ctx context from which to retrieve SharedPreferences from
     * preferences file 'PasswordGenerator'
     * @return list of generated passwords
     */
    fun generate(ctx: Context): ArrayList<String> {
        val prefs = ctx.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE)

        var phonemes = true
        var pwgenFlags = DIGITS or UPPERS

        for (option in pwOptions.toCharArray()) {
            if (prefs.getBoolean(option.toString(), false)) {
                when (option) {
                    '0' -> pwgenFlags = pwgenFlags and DIGITS.inv()
                    'A' -> pwgenFlags = pwgenFlags and UPPERS.inv()
                    'B' -> pwgenFlags = pwgenFlags or AMBIGUOUS
                    's' -> phonemes = false
                    'y' -> pwgenFlags = pwgenFlags or SYMBOLS
                    'v' -> {
                        phonemes = false
                        pwgenFlags = pwgenFlags or NO_VOWELS // | DIGITS | UPPERS;
                    }
                }// pwgenFlags = DIGITS | UPPERS;
            }
        }

        val length = prefs.getInt("length", 8)
        if (length < 5) {
            phonemes = false
        }
        if (length <= 2) {
            pwgenFlags = pwgenFlags and UPPERS.inv()
        }
        if (length <= 1) {
            pwgenFlags = pwgenFlags and DIGITS.inv()
        }

        val passwords = ArrayList<String>()
        val num = prefs.getInt("num", 1)
        for (i in 0 until num) {
            if (phonemes) {
                passwords.add(Phonemes.phonemes(length, pwgenFlags))
            } else {
                passwords.add(RandomPasswordGenerator.rand(length, pwgenFlags))
            }
        }
        return passwords
    }
}

