/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.pwgenxkpwd

import android.content.Context
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.pwgen.PasswordGenerator.PasswordGeneratorException
import com.zeapo.pwdstore.pwgen.secureRandomCharacter
import com.zeapo.pwdstore.pwgen.secureRandomElement
import com.zeapo.pwdstore.pwgen.secureRandomNumber
import java.io.IOException
import java.util.Locale

class PasswordBuilder(ctx: Context) {

    private var numSymbols = 0
    private var isAppendSymbolsSeparator = false
    private var context = ctx
    private var numWords = 3
    private var maxWordLength = 9
    private var minWordLength = 5
    private var separator = "."
    private var capsType = CapsType.Sentence
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
        val numbers = StringBuilder(totalNumbers)
        for (i in 0 until totalNumbers) {
            numbers.append(secureRandomNumber(10))
        }
        return numbers.toString()
    }

    private fun generateRandomSymbolSequence(numSymbols: Int): String {
        val numbers = StringBuilder(numSymbols)
        for (i in 0 until numSymbols) {
            numbers.append(SYMBOLS.secureRandomCharacter())
        }
        return numbers.toString()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Throws(PasswordGeneratorException::class)
    fun create(): String {
        val wordBank = mutableListOf<String>()
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
            for (wordLength in minWordLength..maxWordLength) {
                wordBank.addAll(words[wordLength] ?: emptyList())
            }

            if (wordBank.size == 0) {
                throw PasswordGeneratorException(context.getString(R.string.xkpwgen_builder_error, minWordLength, maxWordLength))
            }

            for (i in 0 until numWords) {
                val candidate = wordBank.secureRandomElement()
                val s = when (capsType) {
                    CapsType.UPPERCASE -> candidate.toUpperCase(Locale.getDefault())
                    CapsType.Sentence -> if (i == 0) candidate.capitalize(Locale.getDefault()) else candidate
                    CapsType.TitleCase -> candidate.capitalize(Locale.getDefault())
                    CapsType.lowercase -> candidate.toLowerCase(Locale.getDefault())
                    CapsType.As_iS -> candidate
                }
                password.append(s)
                if (i + 1 < numWords) {
                    password.append(separator)
                }
            }
        } catch (e: IOException) {
            throw PasswordGeneratorException("Failed generating password!")
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

    companion object {

        private const val SYMBOLS = "!@\$%^&*-_+=:|~?/.;#"
    }
}
