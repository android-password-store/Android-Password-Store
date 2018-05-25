package com.zeapo.pwdstore.autofill;

import com.zeapo.pwdstore.PasswordEntry;

import java.util.List;

public interface OnBatchDecryptedListener {
    void onBatchDecrypted(List<PasswordEntry> results);
}
