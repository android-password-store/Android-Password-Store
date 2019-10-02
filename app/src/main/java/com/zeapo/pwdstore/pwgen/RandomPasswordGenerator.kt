/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0
 */
package com.zeapo.pwdstore.pwgen

internal object RandomPasswordGenerator {

    /**
     * Generates a completely random password.
     *
     * @param size length of password to generate
     * @param pwFlags flag field where set bits indicate conditions the
     * generated password must meet
     * <table summary ="bits of flag field">
     * <tr><td>Bit</td><td>Condition</td></tr>
     * <tr><td>0</td><td>include at least one number</td></tr>
     * <tr><td>1</td><td>include at least one uppercase letter</td></tr>
     * <tr><td>2</td><td>include at least one symbol</td></tr>
     * <tr><td>3</td><td>don't include ambiguous characters</td></tr>
     * <tr><td>4</td><td>don't include vowels</td></tr>
     * <tr><td>5</td><td>include at least one lowercase</td></tr>
    </table> *
     * @return the generated password
     */
    fun rand(size: Int, pwFlags: Int): String {
        var password: String
        var cha: Char
        var i: Int
        var featureFlags: Int
        var num: Int
        var character: String

        var bank = ""
        if (pwFlags and PasswordGenerator.DIGITS > 0) {
            bank += PasswordGenerator.DIGITS_STR
        }
        if (pwFlags and PasswordGenerator.UPPERS > 0) {
            bank += PasswordGenerator.UPPERS_STR
        }
        if (pwFlags and PasswordGenerator.LOWERS > 0) {
            bank += PasswordGenerator.LOWERS_STR
        }
        if (pwFlags and PasswordGenerator.SYMBOLS > 0) {
            bank += PasswordGenerator.SYMBOLS_STR
        }
        do {
            password = ""
            featureFlags = pwFlags
            i = 0
            while (i < size) {
                num = RandomNumberGenerator.number(bank.length)
                cha = bank.toCharArray()[num]
                character = cha.toString()
                if (pwFlags and PasswordGenerator.AMBIGUOUS > 0 &&
                        PasswordGenerator.AMBIGUOUS_STR.contains(character)) {
                    continue
                }
                if (pwFlags and PasswordGenerator.NO_VOWELS > 0 && PasswordGenerator.VOWELS_STR.contains(character)) {
                    continue
                }
                password += character
                i++
                if (PasswordGenerator.DIGITS_STR.contains(character)) {
                    featureFlags = featureFlags and PasswordGenerator.DIGITS.inv()
                }
                if (PasswordGenerator.UPPERS_STR.contains(character)) {
                    featureFlags = featureFlags and PasswordGenerator.UPPERS.inv()
                }
                if (PasswordGenerator.SYMBOLS_STR.contains(character)) {
                    featureFlags = featureFlags and PasswordGenerator.SYMBOLS.inv()
                }
                if (PasswordGenerator.LOWERS_STR.contains(character)) {
                    featureFlags = featureFlags and PasswordGenerator.LOWERS.inv()
                }
            }
        } while (featureFlags and (PasswordGenerator.UPPERS or PasswordGenerator.DIGITS or PasswordGenerator.SYMBOLS or PasswordGenerator.LOWERS) > 0)
        return password
    }
}
