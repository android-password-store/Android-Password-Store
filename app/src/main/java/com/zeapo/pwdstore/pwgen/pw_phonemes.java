package com.zeapo.pwdstore.pwgen;

public class pw_phonemes {
    private static final int CONSONANT  = 0x0001;
    private static final int VOWEL      = 0x0002;
    private static final int DIPTHONG   = 0x0004;
    private static final int NOT_FIRST  = 0x0008;

    private static final element elements[] = {
            new element("a", VOWEL),
            new element("ae", VOWEL | DIPTHONG),
            new element("ah", VOWEL | DIPTHONG),
            new element("ai", VOWEL | DIPTHONG),
            new element("b",  CONSONANT),
            new element("c", CONSONANT),
            new element("ch", CONSONANT | DIPTHONG),
            new element("d", CONSONANT),
            new element("e", VOWEL),
            new element("ee", VOWEL | DIPTHONG),
            new element("ei", VOWEL | DIPTHONG),
            new element("f", CONSONANT),
            new element("g", CONSONANT),
            new element("gh", CONSONANT | DIPTHONG | NOT_FIRST),
            new element("h", CONSONANT),
            new element("i", VOWEL),
            new element("ie", VOWEL | DIPTHONG),
            new element("j", CONSONANT),
            new element("k", CONSONANT),
            new element("l", CONSONANT),
            new element("m", CONSONANT),
            new element("n", CONSONANT),
            new element("ng", CONSONANT | DIPTHONG | NOT_FIRST),
            new element("o", VOWEL),
            new element("oh", VOWEL | DIPTHONG),
            new element("oo", VOWEL | DIPTHONG),
            new element("p", CONSONANT),
            new element("ph", CONSONANT | DIPTHONG),
            new element("qu", CONSONANT | DIPTHONG),
            new element("r", CONSONANT),
            new element("s", CONSONANT),
            new element("sh", CONSONANT | DIPTHONG),
            new element("t", CONSONANT),
            new element("th", CONSONANT | DIPTHONG),
            new element("u", VOWEL),
            new element("v", CONSONANT),
            new element("w", CONSONANT),
            new element("x", CONSONANT),
            new element("y", CONSONANT),
            new element("z", CONSONANT)
    };

    private static class element {
        String str;
        int flags;
        element(String str, int flags) {
            this.str = str;
            this.flags = flags;
        }
    }

    private static final int NUM_ELEMENTS = elements.length;

    /**
     * Generates a human-readable password.
     *
     * @param size    length of password to generate
     * @param pwFlags flag field where set bits indicate conditions the
     *                generated password must meet
     *                <table summary="bits of flag field">
     *                <tr><td>Bit</td><td>Condition</td></tr>
     *                <tr><td>0</td><td>include at least one number</td></tr>
     *                <tr><td>1</td><td>include at least one uppercase letter</td></tr>
     *                <tr><td>2</td><td>include at least one symbol</td></tr>
     *                <tr><td>3</td><td>don't include ambiguous characters</td></tr>
     *                </table>
     * @return the generated password
     */
    public static String phonemes(int size, int pwFlags) {
        String password;
        int curSize, i, length, flags, featureFlags, prev, shouldBe;
        boolean first;
        String str;
        char cha;

        do {
            password = "";
            featureFlags = pwFlags;
            curSize = 0;
            prev = 0;
            first = true;

            shouldBe = randnum.number(2) == 1 ? VOWEL : CONSONANT;

            while (curSize < size) {
                i = randnum.number(NUM_ELEMENTS);
                str = elements[i].str;
                length = str.length();
                flags = elements[i].flags;
                // Filter on the basic type of the next element
                if ((flags & shouldBe) == 0) {
                    continue;
                }
                // Handle the NOT_FIRST flag
                if (first && (flags & NOT_FIRST) > 0) {
                    continue;
                }
                // Don't allow VOWEL followed a Vowel/Dipthong pair
                if ((prev & VOWEL) > 0 && (flags & VOWEL) > 0
                        && (flags & DIPTHONG) > 0) {
                    continue;
                }
                // Don't allow us to overflow the buffer
                if (length > size - curSize) {
                    continue;
                }
                // OK, we found an element which matches our criteria, let's do
                // it
                password += str;

                // Handle UPPERS
                if ((pwFlags & pwgen.UPPERS) > 0) {
                    if ((first || (flags & CONSONANT) > 0)
                            && (randnum.number(10) < 2)) {
                        int index = password.length() - length;
                        password = password.substring(0, index)
                                + str.toUpperCase();
                        featureFlags &= ~pwgen.UPPERS;
                    }
                }

                // Handle the AMBIGUOUS flag
                if ((pwFlags & pwgen.AMBIGUOUS) > 0) {
                    for (char ambiguous : pwgen.AMBIGUOUS_STR.toCharArray()) {
                        if (password.contains(String.valueOf(ambiguous))) {
                            password = password.substring(0, curSize);
                            break;
                        }
                    }
                    if (password.length() == curSize)
                        continue;
                }

                curSize += length;

                // Time to stop?
                if (curSize >= size)
                    break;

                // Handle DIGITS
                if ((pwFlags & pwgen.DIGITS) > 0) {
                    if (!first && (randnum.number(10) < 3)) {
                        String val;
                        do {
                            cha = Character.forDigit(randnum.number(10), 10);
                            val = String.valueOf(cha);
                        } while ((pwFlags & pwgen.AMBIGUOUS) > 0
                                && pwgen.AMBIGUOUS_STR.contains(val));
                        password += val;
                        curSize++;

                        featureFlags &= ~pwgen.DIGITS;

                        first = true;
                        prev = 0;
                        shouldBe = randnum.number(2) == 1 ? VOWEL : CONSONANT;
                        continue;
                    }
                }

                // Handle SYMBOLS
                if ((pwFlags & pwgen.SYMBOLS) > 0) {
                    if (!first && (randnum.number(10) < 2)) {
                        String val;
                        int num;
                        do {
                            num = randnum.number(pwgen.SYMBOLS_STR.length());
                            cha = pwgen.SYMBOLS_STR.toCharArray()[num];
                            val = String.valueOf(cha);
                        } while ((pwFlags & pwgen.AMBIGUOUS) > 0
                                && pwgen.AMBIGUOUS_STR.contains(val));
                        password += val;
                        curSize++;

                        featureFlags &= ~pwgen.SYMBOLS;
                    }
                }

                // OK, figure out what the next element should be
                if (shouldBe == CONSONANT) {
                    shouldBe = VOWEL;
                } else {
                    if ((prev & VOWEL) > 0 || (flags & DIPTHONG) > 0
                            || (randnum.number(10) > 3)) {
                        shouldBe = CONSONANT;
                    } else {
                        shouldBe = VOWEL;
                    }
                }
                prev = flags;
                first = false;
            }
        } while ((featureFlags & (pwgen.UPPERS | pwgen.DIGITS | pwgen.SYMBOLS))
                > 0);
        return password;
    }
}
