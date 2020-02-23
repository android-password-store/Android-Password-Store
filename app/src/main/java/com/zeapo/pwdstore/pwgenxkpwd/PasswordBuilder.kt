/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.pwgenxkpwd

import android.content.Context
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.pwgen.PasswordGenerator
import com.zeapo.pwdstore.pwgen.PasswordGenerator.PasswordGeneratorExeption
import java.io.IOException
import java.security.SecureRandom
import java.util.ArrayList
import java.util.Locale

class PasswordBuilder(ctx: Context) {

    private var numSymbols = 0
    private var isAppendSymbolsSeparator = false
    private var context = ctx
    private var numWords = 3
    private var maxWordLength = 9
    private var minWordLength = 5
    private var separator = "."
    private var capsType = CapsType.Sentencecase
    private var prependDigits = 0
    private var numDigits = 0
    private var isPrependWithSeparator = false
    private var isAppendNumberSeparator = false

    fun setNumberOfWords(amount: Int) = apply {
        numWords = amount
    }

    fun setMinimumWordLength(min: Int) = apply {
        minWordLength = min
    }

    fun setMaximumWordLength(max: Int) = apply {
        maxWordLength = max
    }

    fun setSeparator(separator: String) = apply {
        this.separator = separator
    }

    fun setCapitalization(capitalizationScheme: CapsType) = apply {
        capsType = capitalizationScheme
    }

    @JvmOverloads
    fun prependNumbers(numDigits: Int, addSeparator: Boolean = true) = apply {
        prependDigits = numDigits
        isPrependWithSeparator = addSeparator
    }

    @JvmOverloads
    fun appendNumbers(numDigits: Int, addSeparator: Boolean = false) = apply {
        this.numDigits = numDigits
        isAppendNumberSeparator = addSeparator
    }

    @JvmOverloads
    fun appendSymbols(numSymbols: Int, addSeparator: Boolean = false) = apply {
        this.numSymbols = numSymbols
        isAppendSymbolsSeparator = addSeparator
    }

    private fun generateRandomNumberSequence(totalNumbers: Int): String {
        val secureRandom = SecureRandom()
        val numbers = StringBuilder(totalNumbers)

        for (i in 0 until totalNumbers) {
            numbers.append(secureRandom.nextInt(10))
        }
        return numbers.toString()
    }

    private fun generateRandomSymbolSequence(numSymbols: Int): Any {
        val secureRandom = SecureRandom()
        val numbers = StringBuilder(numSymbols)

        for (i in 0 until numSymbols) {
            numbers.append(SYMBOLS[secureRandom.nextInt(SYMBOLS.length)])
        }
        return numbers.toString()
    }

    @Throws(PasswordGenerator.PasswordGeneratorExeption::class)
    fun create(): String {
        val wordBank = ArrayList<String>()
        val secureRandom = SecureRandom()
        val password = StringBuilder()

        if (prependDigits != 0) {
            password.append(generateRandomNumberSequence(prependDigits))
            if (isPrependWithSeparator) {
                password.append(separator)
            }
        }
        try {
            val dictionary = XkpwdDictionary(context)
            val words = dictionary.words
            for (wordLength in words.keys) {
                if (wordLength in minWordLength..maxWordLength) {
                    wordBank.addAll(words[wordLength]!!)
                }
            }

            if (wordBank.size == 0) {
                throw PasswordGeneratorExeption(context.getString(R.string.xkpwgen_builder_error, minWordLength, maxWordLength))
            }

            for (i in 0 until numWords) {
                val randomIndex = secureRandom.nextInt(wordBank.size)
                var s = wordBank[randomIndex]

                if (capsType != CapsType.As_iS) {
                    s = s.toLowerCase(Locale.getDefault())
                    when (capsType) {
                        CapsType.UPPERCASE -> s = s.toUpperCase(Locale.getDefault())
                        CapsType.Sentencecase -> {
                            if (i == 0) {
                                s = capitalize(s)
                            }
                        }
                        CapsType.TitleCase -> {
                            s = capitalize(s)
                        }
                    }
                }
                password.append(s)
                wordBank.removeAt(randomIndex)
                if (i + 1 < numWords) {
                    password.append(separator)
                }
            }
        } catch (e: IOException) {
            throw PasswordGeneratorExeption("Failed generating password!")
        }
        if (numDigits != 0) {
            if (isAppendNumberSeparator) {
                password.append(separator)
            }
            password.append(generateRandomNumberSequence(numDigits))
        }
        if (numSymbols != 0) {
            if (isAppendSymbolsSeparator) {
                password.append(separator)
            }
            password.append(generateRandomSymbolSequence(numSymbols))
        }
        return password.toString()
    }

    private fun capitalize(s: String): String {
        var result = s
        val lower = result.toLowerCase(Locale.getDefault())
        result = lower.substring(0, 1).toUpperCase(Locale.getDefault()) + result.substring(1)
        return result
    }

    companion object {
        private const val SYMBOLS = "!@\$%^&*-_+=:|~?/.;#"
    }
}
