package com.zeapo.pwdstore.autofill;

public final class Heuristics {
    public static final String[] LOGIN_FIELD_EXAMPLES = {
            "username",
            "login",
            "kdnr",
            "kto",
            "un",
            "user",
            "email",
            "id"
    };

    public static final String[] PASSWORD_FIELD_EXAMPLES = {
            "password",
            "pw",
            "secret",
    };

    private static Boolean compareHintWithExamples(String hint, String[] examples) {
        for (String example : examples) {
            if (example.contains(hint.toLowerCase())) return true;
        }
        return false;
    }

    public static Boolean mightBeLoginField(String hint) {
        return compareHintWithExamples(hint, LOGIN_FIELD_EXAMPLES);
    }

    public static Boolean mightBePasswordField(String hint) {
        return compareHintWithExamples(hint, PASSWORD_FIELD_EXAMPLES);
    }
}
