package com.zeapo.pwdstore.pwgen;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.ArrayList;

import javax.inject.Inject;

public class RandomPasswordGenerator {
    private final int DIGITS = 0x0001;
    private final int AMBIGUOUS = 0x0008;
    private final int NO_VOWELS = 0x0010;
    private final String SYMBOLS_STR;
    private final String AMBIGUOUS_STR;
    private final int UPPERS = 0x0002;
    private final int SYMBOLS = 0x0004;
    private final int CONSONANT = 0x0001;
    private final int VOWEL = 0x0002;
    private final int DIPHTHONG = 0x0004;
    private final int NOT_FIRST = 0x0008;
    private final element elements[] = {
            new element("a", VOWEL),
            new element("ae", VOWEL | DIPHTHONG),
            new element("ah", VOWEL | DIPHTHONG),
            new element("ai", VOWEL | DIPHTHONG),
            new element("b", CONSONANT),
            new element("c", CONSONANT),
            new element("ch", CONSONANT | DIPHTHONG),
            new element("d", CONSONANT),
            new element("e", VOWEL),
            new element("ee", VOWEL | DIPHTHONG),
            new element("ei", VOWEL | DIPHTHONG),
            new element("f", CONSONANT),
            new element("g", CONSONANT),
            new element("gh", CONSONANT | DIPHTHONG | NOT_FIRST),
            new element("h", CONSONANT),
            new element("i", VOWEL),
            new element("ie", VOWEL | DIPHTHONG),
            new element("j", CONSONANT),
            new element("k", CONSONANT),
            new element("l", CONSONANT),
            new element("m", CONSONANT),
            new element("n", CONSONANT),
            new element("ng", CONSONANT | DIPHTHONG | NOT_FIRST),
            new element("o", VOWEL),
            new element("oh", VOWEL | DIPHTHONG),
            new element("oo", VOWEL | DIPHTHONG),
            new element("p", CONSONANT),
            new element("ph", CONSONANT | DIPHTHONG),
            new element("qu", CONSONANT | DIPHTHONG),
            new element("r", CONSONANT),
            new element("s", CONSONANT),
            new element("sh", CONSONANT | DIPHTHONG),
            new element("t", CONSONANT),
            new element("th", CONSONANT | DIPHTHONG),
            new element("u", VOWEL),
            new element("v", CONSONANT),
            new element("w", CONSONANT),
            new element("x", CONSONANT),
            new element("y", CONSONANT),
            new element("z", CONSONANT)
    };
    private final int NUM_ELEMENTS = elements.length;
    // No a, c, n, h, H, C, 1, N
    private final String pwOptions = "0ABsvy";
    private SecureRandom secureRandom;

    @Inject
    RandomPasswordGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
        AMBIGUOUS_STR = "B8G6I1l0OQDS5Z2";
        SYMBOLS_STR = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    }

    /**
     * Generates a completely random password.
     *
     * @param size    length of password to generate
     * @param pwFlags flag field where set bits indicate conditions the
     *                generated password must meet
     *                <table summary ="bits of flag field">
     *                <tr><td>Bit</td><td>Condition</td></tr>
     *                <tr><td>0</td><td>include at least one number</td></tr>
     *                <tr><td>1</td><td>include at least one uppercase letter</td></tr>
     *                <tr><td>2</td><td>include at least one symbol</td></tr>
     *                <tr><td>3</td><td>don't include ambiguous characters</td></tr>
     *                <tr><td>4</td><td>don't include vowels</td></tr>
     *                </table>
     * @return the generated password
     */
    public String getRandomPassword(int size, int pwFlags) {
        String password;
        char cha;
        int i, featureFlags, num;
        String val;

        String bank = "";
        String DIGITS_STR = "0123456789";
        if ((pwFlags & DIGITS) > 0) {
            bank += DIGITS_STR;
        }
        String UPPERS_STR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        if ((pwFlags & UPPERS) > 0) {
            bank += UPPERS_STR;
        }
        String LOWERS_STR = "abcdefghijklmnopqrstuvwxyz";
        bank += LOWERS_STR;
        if ((pwFlags & SYMBOLS) > 0) {
            bank += SYMBOLS_STR;
        }
        do {
            password = "";
            featureFlags = pwFlags;
            i = 0;
            while (i < size) {
                num = secureRandom.nextInt(bank.length());
                cha = bank.toCharArray()[num];
                val = String.valueOf(cha);
                if ((pwFlags & AMBIGUOUS) > 0
                        && AMBIGUOUS_STR.contains(val)) {
                    continue;
                }
                String VOWELS_STR = "01aeiouyAEIOUY";
                if ((pwFlags & NO_VOWELS) > 0
                        && VOWELS_STR.contains(val)) {
                    continue;
                }
                password += val;
                i++;
                if (DIGITS_STR.contains(val)) {
                    featureFlags &= ~DIGITS;
                }
                if (UPPERS_STR.contains(val)) {
                    featureFlags &= ~UPPERS;
                }
                if (SYMBOLS_STR.contains(val)) {
                    featureFlags &= ~SYMBOLS;
                }
            }
        } while ((featureFlags & (UPPERS | DIGITS | SYMBOLS))
                > 0);
        return password;
    }

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
    public String getHumanReadablePassword(int size, int pwFlags) {
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

            shouldBe = secureRandom.nextInt(2) == 1 ? VOWEL : CONSONANT;

            while (curSize < size) {
                i = secureRandom.nextInt(NUM_ELEMENTS);
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
                        && (flags & DIPHTHONG) > 0) {
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
                if ((pwFlags & UPPERS) > 0) {
                    if ((first || (flags & CONSONANT) > 0)
                            && (secureRandom.nextInt(10) < 2)) {
                        int index = password.length() - length;
                        password = password.substring(0, index)
                                + str.toUpperCase();
                        featureFlags &= ~UPPERS;
                    }
                }

                // Handle the AMBIGUOUS flag
                if ((pwFlags & AMBIGUOUS) > 0) {
                    for (char ambiguous : AMBIGUOUS_STR.toCharArray()) {
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
                if ((pwFlags & DIGITS) > 0) {
                    if (!first && (secureRandom.nextInt(10) < 3)) {
                        String val;
                        do {
                            cha = Character.forDigit(secureRandom.nextInt(10), 10);
                            val = String.valueOf(cha);
                        } while ((pwFlags & AMBIGUOUS) > 0
                                && AMBIGUOUS_STR.contains(val));
                        password += val;
                        curSize++;

                        featureFlags &= ~DIGITS;

                        first = true;
                        prev = 0;
                        shouldBe = secureRandom.nextInt(2) == 1 ? VOWEL : CONSONANT;
                        continue;
                    }
                }

                // Handle SYMBOLS
                if ((pwFlags & SYMBOLS) > 0) {
                    if (!first && (secureRandom.nextInt(10) < 2)) {
                        String val;
                        int num;
                        do {
                            num = secureRandom.nextInt(SYMBOLS_STR.length());
                            cha = SYMBOLS_STR.toCharArray()[num];
                            val = String.valueOf(cha);
                        } while ((pwFlags & AMBIGUOUS) > 0
                                && AMBIGUOUS_STR.contains(val));
                        password += val;
                        curSize++;

                        featureFlags &= ~SYMBOLS;
                    }
                }

                // OK, figure out what the next element should be
                if (shouldBe == CONSONANT) {
                    shouldBe = VOWEL;
                } else {
                    if ((prev & VOWEL) > 0 || (flags & DIPHTHONG) > 0
                            || (secureRandom.nextInt(10) > 3)) {
                        shouldBe = CONSONANT;
                    } else {
                        shouldBe = VOWEL;
                    }
                }
                prev = flags;
                first = false;
            }
        } while ((featureFlags & (UPPERS | DIGITS | SYMBOLS))
                > 0);
        return password;
    }

    /**
     * Sets password generation preferences.
     *
     * @param ctx     context from which to retrieve SharedPreferences from
     *                preferences file 'pwgen'
     * @param argv    options for password generation
     *                <table summary="options for password generation">
     *                <tr><td>Option</td><td>Description</td></tr>
     *                <tr><td>0</td><td>don't include numbers</td></tr>
     *                <tr><td>A</td><td>don't include uppercase letters</td></tr>
     *                <tr><td>B</td><td>don't include ambiguous charactersl</td></tr>
     *                <tr><td>s</td><td>generate completely random passwords</td></tr>
     *                <tr><td>v</td><td>don't include vowels</td></tr>
     *                <tr><td>y</td><td>include at least one symbol</td></tr>
     *                </table>
     * @param numArgv numerical options for password generation: length of
     *                generated passwords followed by number of passwords to
     *                generate
     * @return <code>false</code> if a numerical options is invalid,
     * <code>true</code> otherwise
     */
    public boolean setPrefs(Context ctx, ArrayList<String> argv
            , int... numArgv) {
        SharedPreferences prefs
                = ctx.getSharedPreferences("pwgen", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        for (char option : pwOptions.toCharArray()) {
            if (argv.contains(String.valueOf(option))) {
                editor.putBoolean(String.valueOf(option), true);
                argv.remove(String.valueOf(option));
            } else {
                editor.putBoolean(String.valueOf(option), false);
            }
        }
        for (int i = 0; i < numArgv.length && i < 2; i++) {
            if (numArgv[i] <= 0) {
                // Invalid password length or number of passwords
                return false;
            }
            String name = i == 0 ? "length" : "num";
            editor.putInt(name, numArgv[i]);
        }
        editor.apply();
        return true;
    }

    /**
     * Generates passwords using the preferences set by
     * {@link #setPrefs(Context, ArrayList, int...)}.
     *
     * @param ctx context from which to retrieve SharedPreferences from
     *            preferences file 'pwgen'
     * @return list of generated passwords
     */
    public ArrayList<String> generate(Context ctx) {
        SharedPreferences prefs
                = ctx.getSharedPreferences("pwgen", Context.MODE_PRIVATE);

        boolean phonemes = true;
        int pwgenFlags = DIGITS | UPPERS;

        for (char option : pwOptions.toCharArray()) {
            if (prefs.getBoolean(String.valueOf(option), false)) {
                switch (option) {
                    case '0':
                        pwgenFlags &= ~DIGITS;
                        break;
                    case 'A':
                        pwgenFlags &= ~UPPERS;
                        break;
                    case 'B':
                        pwgenFlags |= AMBIGUOUS;
                        break;
                    case 's':
                        phonemes = false;
                        // pwgenFlags = DIGITS | UPPERS;
                        break;
                    case 'y':
                        pwgenFlags |= SYMBOLS;
                        break;
                    case 'v':
                        phonemes = false;
                        pwgenFlags |= NO_VOWELS; // | DIGITS | UPPERS;
                        break;
                }
            }
        }

        int length = prefs.getInt("length", 8);
        if (length < 5) {
            phonemes = false;
        }
        if (length <= 2) {
            pwgenFlags &= ~UPPERS;
        }
        if (length <= 1) {
            pwgenFlags &= ~DIGITS;
        }

        ArrayList<String> passwords = new ArrayList<>();
        int num = prefs.getInt("num", 1);
        for (int i = 0; i < num; i++) {
            if (phonemes) {
                passwords.add(getHumanReadablePassword(length, pwgenFlags));
            } else {
                passwords.add(getRandomPassword(length, pwgenFlags));
            }
        }
        return passwords;
    }

    private static class element {
        String str;
        int flags;

        element(String str, int flags) {
            this.str = str;
            this.flags = flags;
        }
    }
}
