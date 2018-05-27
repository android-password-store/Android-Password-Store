package com.zeapo.pwdstore.autofill;

import android.annotation.TargetApi;
import android.content.Intent;

@TargetApi(26)
public class PgpAuthenticationRequiredException extends Exception {
    Intent result;
    public PgpAuthenticationRequiredException(Intent result) {
        this.result = result;
    }
}
