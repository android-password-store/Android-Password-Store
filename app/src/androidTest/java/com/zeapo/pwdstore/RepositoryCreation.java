package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;

public class RepositoryCreation extends ActivityInstrumentationTestCase2<PasswordStore> {
    private Activity passwordStore;
    Instrumentation mInstrumentation;
    SharedPreferences settings;

    public RepositoryCreation() {
        super(PasswordStore.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        Intent intent = new Intent();
        setActivityIntent(intent);

        passwordStore = getActivity(); // get a references to the app under test
        assertNotNull(passwordStore);
        settings = PreferenceManager.getDefaultSharedPreferences(passwordStore.getApplicationContext());
        settings.edit().clear().apply();
    }

    /**
     * If we change from ssh protocol to https we automatically switch to username/password auth
     * @throws Exception
     */
    public void testSpinnerChange() throws Exception{
    }
}
