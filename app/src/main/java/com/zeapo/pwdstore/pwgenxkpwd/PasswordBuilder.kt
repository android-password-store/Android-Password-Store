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
    private var numWords = 4
    private var maxWordLength = 8
    private var minWordLength = 5
    private var separator = "."
    private var capType = CapsType.Sentencecase
    private var prependDigits = 0
    private var numDigits = 0
    private var isPrependWithSeparator = false
    private var isAppendNumberSeparator = false

    fun setNumberOfWords(amount: Int): PasswordBuilder {
        numWords = amount
        return this
    }

    fun setMinimumWordLength(min: Int): PasswordBuilder {
        minWordLength = min
        return this
    }

    fun setMaximumWordLength(max: Int): PasswordBuilder {
        maxWordLength = max
        return this
    }

    fun setSeparator(separator: String): PasswordBuilder {
        this.separator = separator
        return this
    }

    fun setCapitalization(capitalizationScheme: CapsType): PasswordBuilder {
        capType = capitalizationScheme
        return this
    }

    @JvmOverloads
    fun prependNumbers(numDigits: Int, addSeparator: Boolean = true): PasswordBuilder {
        prependDigits = numDigits
        isPrependWithSeparator = addSeparator
        return this
    }

    @JvmOverloads
    fun appendNumbers(numDigits: Int, addSeparator: Boolean = false): PasswordBuilder {
        this.numDigits = numDigits
        isAppendNumberSeparator = addSeparator
        return this
    }

    @JvmOverloads
    fun appendSymbols(numSymbols: Int, addSeparator: Boolean = false): PasswordBuilder {
        this.numSymbols = numSymbols
        isAppendSymbolsSeparator = addSeparator
        return this
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

                if (capType != CapsType.As_iS) {
                    s = s.toLowerCase(Locale.getDefault())
                    when (capType) {
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
