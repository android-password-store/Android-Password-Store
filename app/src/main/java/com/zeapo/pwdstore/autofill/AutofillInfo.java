package com.zeapo.pwdstore.autofill;

import android.annotation.TargetApi;
import android.view.autofill.AutofillId;

@TargetApi(26)
class AutofillInfo {
    String hint;
    AutofillId autofillId;
    int type;
    String webDomain;

    public AutofillInfo(String hint, AutofillId id, int type, String webDomain) {
        this.hint = hint;
        this.autofillId = id;
        this.type = type;
        this.webDomain = webDomain;
    }

    @Override
    public String toString() {
        return "AutofillId@" +autofillId + " hint:" + hint + " type:" + type +
                " webDomain: " + webDomain;
    }
}