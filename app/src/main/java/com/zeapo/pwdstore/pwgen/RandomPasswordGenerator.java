package com.zeapo.pwdstore.pwgen;

class RandomPasswordGenerator {

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
        if ((pwFlags & PasswordGenerator.DIGITS) > 0) {
            bank += PasswordGenerator.DIGITS_STR;
        }
        if ((pwFlags & PasswordGenerator.UPPERS) > 0) {
            bank += PasswordGenerator.UPPERS_STR;
        }
        bank += PasswordGenerator.LOWERS_STR;
        if ((pwFlags & PasswordGenerator.SYMBOLS) > 0) {
            bank += PasswordGenerator.SYMBOLS_STR;
        }
        do {
            password = "";
            featureFlags = pwFlags;
            i = 0;
            while (i < size) {
                num = RandomNumberGenerator.number(bank.length());
                cha = bank.toCharArray()[num];
                val = String.valueOf(cha);
                if ((pwFlags & PasswordGenerator.AMBIGUOUS) > 0
                        && PasswordGenerator.AMBIGUOUS_STR.contains(val)) {
                    continue;
                }
                if ((pwFlags & PasswordGenerator.NO_VOWELS) > 0
                        && PasswordGenerator.VOWELS_STR.contains(val)) {
                    continue;
                }
                password += val;
                i++;
                if (PasswordGenerator.DIGITS_STR.contains(val)) {
                    featureFlags &= ~PasswordGenerator.DIGITS;
                }
                if (PasswordGenerator.UPPERS_STR.contains(val)) {
                    featureFlags &= ~PasswordGenerator.UPPERS;
                }
                if (PasswordGenerator.SYMBOLS_STR.contains(val)) {
                    featureFlags &= ~PasswordGenerator.SYMBOLS;
                }
            }
        } while ((featureFlags & (PasswordGenerator.UPPERS | PasswordGenerator.DIGITS | PasswordGenerator.SYMBOLS))
                > 0);
        return password;
    }
}
