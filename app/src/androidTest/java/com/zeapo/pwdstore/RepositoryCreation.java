package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;

public class RepositoryCreation extends ActivityInstrumentationTestCase2<PasswordStore> {

    public RepositoryCreation() {
        super(PasswordStore.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
        Instrumentation mInstrumentation = getInstrumentation();
        Intent intent = new Intent();
        setActivityIntent(intent);

        Activity passwordStore = getActivity();
        assertNotNull(passwordStore);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(passwordStore.getApplicationContext());
        settings.edit().clear().apply();
    }

    /**
     * If we change from ssh protocol to https we automatically switch to username/password auth
     */
    public void testSpinnerChange() throws Exception{
    }
}
