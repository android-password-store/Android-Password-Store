package com.zeapo.pwdstore;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/**
 * A single entry in password store.
 */
public class PasswordEntry {

    private static final String[] USERNAME_FIELDS = new String[]{"login", "username"};

    private final String extraContent;
    private final String password;
    private final String username;

    public PasswordEntry(final ByteArrayOutputStream os) throws UnsupportedEncodingException {
        this(os.toString("UTF-8"));
    }

    public PasswordEntry(final String decryptedContent) {
        final String[] passContent = decryptedContent.split("\n", 2);
        password = passContent[0];
        extraContent = passContent.length > 1 ? passContent[1] : "";
        username = findUsername();
    }

    public String getPassword() {
        return password;
    }

    public String getExtraContent() {
        return extraContent;
    }

    public String getUsername() {
        return username;
    }

    public boolean hasExtraContent() {
        return extraContent.length() != 0;
    }

    public boolean hasUsername() {
        return username != null;
    }

    private String findUsername() {
        final String[] extraLines = extraContent.split("\n");
        for (String line : extraLines) {
            for (String field : USERNAME_FIELDS) {
                if (line.toLowerCase().startsWith(field + ":")) {
                    return line.split(": *", 2)[1];
                }
            }
        }
        return null;
    }
}
