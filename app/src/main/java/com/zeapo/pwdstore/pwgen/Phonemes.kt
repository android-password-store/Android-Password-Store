package com.zeapo.pwdstore.pwgen

internal object Phonemes {
    private const val CONSONANT = 0x0001
    private const val VOWEL = 0x0002
    private const val DIPTHONG = 0x0004
    private const val NOT_FIRST = 0x0008

    private val elements = arrayOf(
            Element("a", VOWEL),
            Element("ae", VOWEL or DIPTHONG),
            Element("ah", VOWEL or DIPTHONG),
            Element("ai", VOWEL or DIPTHONG),
            Element("b", CONSONANT),
            Element("c", CONSONANT),
            Element("ch", CONSONANT or DIPTHONG),
            Element("d", CONSONANT),
            Element("e", VOWEL),
            Element("ee", VOWEL or DIPTHONG),
            Element("ei", VOWEL or DIPTHONG),
            Element("f", CONSONANT),
            Element("g", CONSONANT),
            Element("gh", CONSONANT or DIPTHONG or NOT_FIRST),
            Element("h", CONSONANT),
            Element("i", VOWEL),
            Element("ie", VOWEL or DIPTHONG),
            Element("j", CONSONANT),
            Element("k", CONSONANT),
            Element("l", CONSONANT),
            Element("m", CONSONANT),
            Element("n", CONSONANT),
            Element("ng", CONSONANT or DIPTHONG or NOT_FIRST),
            Element("o", VOWEL),
            Element("oh", VOWEL or DIPTHONG),
            Element("oo", VOWEL or DIPTHONG),
            Element("p", CONSONANT),
            Element("ph", CONSONANT or DIPTHONG),
            Element("qu", CONSONANT or DIPTHONG),
            Element("r", CONSONANT),
            Element("s", CONSONANT),
            Element("sh", CONSONANT or DIPTHONG),
            Element("t", CONSONANT),
            Element("th", CONSONANT or DIPTHONG),
            Element("u", VOWEL),
            Element("v", CONSONANT),
            Element("w", CONSONANT),
            Element("x", CONSONANT),
            Element("y", CONSONANT),
            Element("z", CONSONANT)
    )

    private val NUM_ELEMENTS = elements.size

    private class Element internal constructor(internal var str: String, internal var flags: Int)

    /**
     * Generates a human-readable password.
     *
     * @param size length of password to generate
     * @param pwFlags flag field where set bits indicate conditions the
     * generated password must meet
     * <table summary="bits of flag field">
     * <tr><td>Bit</td><td>Condition</td></tr>
     * <tr><td>0</td><td>include at least one number</td></tr>
     * <tr><td>1</td><td>include at least one uppercase letter</td></tr>
     * <tr><td>2</td><td>include at least one symbol</td></tr>
     * <tr><td>3</td><td>don't include ambiguous characters</td></tr>
     * <tr><td>5</td><td>include at least one lowercase letter</td></tr>
    </table> *
     * @return the generated password
     */
    fun phonemes(size: Int, pwFlags: Int): String {
        var password: String
        var curSize: Int
        var i: Int
        var length: Int
        var flags: Int
        var featureFlags: Int
        var prev: Int
        var shouldBe: Int
        var first: Boolean
        var str: String
        var cha: Char

        do {
            password = ""
            featureFlags = pwFlags
            curSize = 0
            prev = 0
            first = true

            shouldBe = if (RandomNumberGenerator.number(2) == 1) VOWEL else CONSONANT

            while (curSize < size) {
                i = RandomNumberGenerator.number(NUM_ELEMENTS)
                str = elements[i].str
                length = str.length
                flags = elements[i].flags
                // Filter on the basic type of the next Element
                if (flags and shouldBe == 0) {
                    continue
                }
                // Handle the NOT_FIRST flag
                if (first && flags and NOT_FIRST > 0) {
                    continue
                }
                // Don't allow VOWEL followed a Vowel/Dipthong pair
                if (prev and VOWEL > 0 && flags and VOWEL > 0 &&
                        flags and DIPTHONG > 0
                ) {
                    continue
                }
                // Don't allow us to overflow the buffer
                if (length > size - curSize) {
                    continue
                }
                // OK, we found an Element which matches our criteria, let's do
                // it
                password += str

                // Handle UPPERS
                if (pwFlags and PasswordGenerator.UPPERS > 0) {
                    if ((pwFlags and PasswordGenerator.LOWERS == 0) ||
                            (first || flags and CONSONANT > 0) && RandomNumberGenerator.number(10) < 2) {
                        val index = password.length - length
                        password = password.substring(0, index) + str.toUpperCase()
                        featureFlags = featureFlags and PasswordGenerator.UPPERS.inv()
                    }
                }

                // Handle the AMBIGUOUS flag
                if (pwFlags and PasswordGenerator.AMBIGUOUS > 0) {
                    for (ambiguous in PasswordGenerator.AMBIGUOUS_STR.toCharArray()) {
                        if (password.contains(ambiguous.toString())) {
                            password = password.substring(0, curSize)

                            // Still have upper letters
                            if ((pwFlags and PasswordGenerator.UPPERS) > 0) {
                                featureFlags = featureFlags or PasswordGenerator.UPPERS
                                for (upper in PasswordGenerator.UPPERS_STR.toCharArray()) {
                                    if (password.contains(upper.toString())) {
                                        featureFlags = featureFlags and PasswordGenerator.UPPERS.inv()
                                        break
                                    }
                                }
                            }
                            break
                        }
                    }
                    if (password.length == curSize)
                        continue
                }

                curSize += length

                // Time to stop?
                if (curSize >= size)
                    break

                // Handle DIGITS
                if (pwFlags and PasswordGenerator.DIGITS > 0) {
                    if (!first && RandomNumberGenerator.number(10) < 3) {
                        var character: String
                        do {
                            cha = Character.forDigit(RandomNumberGenerator.number(10), 10)
                            character = cha.toString()
                        } while (pwFlags and PasswordGenerator.AMBIGUOUS > 0 &&
                                PasswordGenerator.AMBIGUOUS_STR.contains(character))
                        password += character
                        curSize++

                        featureFlags = featureFlags and PasswordGenerator.DIGITS.inv()

                        first = true
                        prev = 0
                        shouldBe = if (RandomNumberGenerator.number(2) == 1) VOWEL else CONSONANT
                        continue
                    }
                }

                // Handle SYMBOLS
                if (pwFlags and PasswordGenerator.SYMBOLS > 0) {
                    if (!first && RandomNumberGenerator.number(10) < 2) {
                        var character: String
                        var num: Int
                        do {
                            num = RandomNumberGenerator.number(PasswordGenerator.SYMBOLS_STR.length)
                            cha = PasswordGenerator.SYMBOLS_STR.toCharArray()[num]
                            character = cha.toString()
                        } while (pwFlags and PasswordGenerator.AMBIGUOUS > 0 &&
                                PasswordGenerator.AMBIGUOUS_STR.contains(character))
                        password += character
                        curSize++

                        featureFlags = featureFlags and PasswordGenerator.SYMBOLS.inv()
                    }
                }

                // OK, figure out what the next Element should be
                shouldBe = if (shouldBe == CONSONANT) {
                    VOWEL
                } else {
                    if (prev and VOWEL > 0 || flags and DIPTHONG > 0 ||
                            RandomNumberGenerator.number(10) > 3
                    ) {
                        CONSONANT
                    } else {
                        VOWEL
                    }
                }
                prev = flags
                first = false
            }
        } while (featureFlags and (PasswordGenerator.UPPERS or PasswordGenerator.DIGITS or PasswordGenerator.SYMBOLS) > 0)
        return password
    }
}
