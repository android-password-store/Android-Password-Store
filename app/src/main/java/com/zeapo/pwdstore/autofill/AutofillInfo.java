package com.zeapo.pwdstore.autofill;

import android.view.autofill.AutofillId;

class AutofillInfo {
    AutofillId autofillId;
    int type;
    String webDomain;

    public AutofillInfo(AutofillId id, int type, String webDomain) {
        this.autofillId = id;
        this.type = type;
        this.webDomain = webDomain;
    }
}