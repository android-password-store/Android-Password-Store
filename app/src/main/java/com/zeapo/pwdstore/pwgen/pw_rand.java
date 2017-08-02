package com.zeapo.pwdstore.pwgen;

class pw_rand {

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
    public static String rand(int size, int pwFlags) {
        String password;
        char cha;
        int i, featureFlags, num;
        String val;

        String bank = "";
        if ((pwFlags & pwgen.DIGITS) > 0) {
            bank += pwgen.DIGITS_STR;
        }
        if ((pwFlags & pwgen.UPPERS) > 0) {
            bank += pwgen.UPPERS_STR;
        }
        bank += pwgen.LOWERS_STR;
        if ((pwFlags & pwgen.SYMBOLS) > 0) {
            bank += pwgen.SYMBOLS_STR;
        }
        do {
            password = "";
            featureFlags = pwFlags;
            i = 0;
            while (i < size) {
                num = randnum.number(bank.length());
                cha = bank.toCharArray()[num];
                val = String.valueOf(cha);
                if ((pwFlags & pwgen.AMBIGUOUS) > 0
                        && pwgen.AMBIGUOUS_STR.contains(val)) {
                    continue;
                }
                if ((pwFlags & pwgen.NO_VOWELS) > 0
                        && pwgen.VOWELS_STR.contains(val)) {
                    continue;
                }
                password += val;
                i++;
                if (pwgen.DIGITS_STR.contains(val)) {
                    featureFlags &= ~pwgen.DIGITS;
                }
                if (pwgen.UPPERS_STR.contains(val)) {
                    featureFlags &= ~pwgen.UPPERS;
                }
                if (pwgen.SYMBOLS_STR.contains(val)) {
                    featureFlags &= ~pwgen.SYMBOLS;
                }
            }
        } while ((featureFlags & (pwgen.UPPERS | pwgen.DIGITS | pwgen.SYMBOLS))
                > 0);
        return password;
    }
}
