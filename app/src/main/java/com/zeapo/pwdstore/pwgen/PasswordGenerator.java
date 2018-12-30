package com.zeapo.pwdstore.pwgen;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;

public class PasswordGenerator {
    static final int DIGITS     = 0x0001;
    static final int UPPERS     = 0x0002;
    static final int SYMBOLS    = 0x0004;
    static final int AMBIGUOUS  = 0x0008;
    static final int NO_VOWELS  = 0x0010;

    static final String DIGITS_STR = "0123456789";
    static final String UPPERS_STR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String LOWERS_STR = "abcdefghijklmnopqrstuvwxyz";
    static final String SYMBOLS_STR = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
    static final String AMBIGUOUS_STR = "B8G6I1l0OQDS5Z2";
    static final String VOWELS_STR = "01aeiouyAEIOUY";

    // No a, c, n, h, H, C, 1, N
    private static final String pwOptions = "0ABsvy";

    /**
     * Sets password generation preferences.
     *
     * @param ctx     context from which to retrieve SharedPreferences from
     *                preferences file 'PasswordGenerator'
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
    public static boolean setPrefs(Context ctx, ArrayList<String> argv
            , int... numArgv) {
        SharedPreferences prefs
                = ctx.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE);
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
     *            preferences file 'PasswordGenerator'
     * @return list of generated passwords
     */
    public static ArrayList<String> generate(Context ctx) {
        SharedPreferences prefs
                = ctx.getSharedPreferences("PasswordGenerator", Context.MODE_PRIVATE);

        boolean phonemes = true;
        int pwgenFlags = DIGITS | UPPERS;

        for (char option : pwOptions.toCharArray()) {
            if (prefs.getBoolean(String.valueOf(option), false)) {
                switch(option) {
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
                passwords.add(Phonemes.phonemes(length, pwgenFlags));
            } else {
                passwords.add(RandomPasswordGenerator.rand(length, pwgenFlags));
            }
        }
        return passwords;
    }

}

