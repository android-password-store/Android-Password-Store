package com.zeapo.pwdstore;

import android.net.Uri;

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
    private final String totpSecret;
    private final String hotpSecret;
    private final Long hotpCounter;

    public PasswordEntry(final ByteArrayOutputStream os) throws UnsupportedEncodingException {
        this(os.toString("UTF-8"));
    }

    public PasswordEntry(final String decryptedContent) {
        final String[] passContent = decryptedContent.split("\n", 2);
        password = passContent[0];
        totpSecret = findTotpSecret(decryptedContent);
        hotpSecret = findHotpSecret(decryptedContent);
        hotpCounter = findHotpCounter(decryptedContent);
        extraContent = findExtraContent(passContent);
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

    public String getTotpSecret() {
        return totpSecret;
    }

    public Long getHotpCounter() {
        return hotpCounter;
    }

    public String getHotpSecret() {
        return hotpSecret;
    }

    public boolean hasExtraContent() {
        return extraContent.length() != 0;
    }

    public boolean hasUsername() {
        return username != null;
    }

    public boolean hasTotp() {
        return totpSecret != null;
    }

    public boolean hasHotp() {
        return hotpSecret != null && hotpCounter != null;
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

    private String findTotpSecret(String decryptedContent) {
        for (String line : decryptedContent.split("\n")) {
            if (line.startsWith("otpauth://totp/")) {
                return Uri.parse(line).getQueryParameter("secret");
            }
        }
        return null;
    }

    private String findHotpSecret(String decryptedContent) {
        for (String line : decryptedContent.split("\n")) {
            if (line.startsWith("otpauth://hotp/")) {
                return Uri.parse(line).getQueryParameter("secret");
            }
        }
        return null;
    }

    private Long findHotpCounter(String decryptedContent) {
        for (String line : decryptedContent.split("\n")) {
            if (line.startsWith("otpauth://hotp/")) {
                return Long.parseLong(Uri.parse(line).getQueryParameter("counter")) + 1;
            }
        }
        return null;
    }

    private String findExtraContent(String [] passContent) {
        String extraContent = passContent.length > 1 ? passContent[1] : "";
        // if there is a HOTP URI, we must return the extra content with the counter incremented
        if (hasHotp()) {
            return extraContent.replaceFirst("counter=[0-9]+", "counter=" + Long.toString(hotpCounter));
        }
        return extraContent;
    }
}
