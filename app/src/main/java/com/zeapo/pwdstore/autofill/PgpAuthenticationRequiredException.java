package com.zeapo.pwdstore.autofill;

import android.content.Intent;

public class PgpAuthenticationRequiredException extends Exception {
    Intent result;
    public PgpAuthenticationRequiredException(Intent result) {
        this.result = result;
    }
}
