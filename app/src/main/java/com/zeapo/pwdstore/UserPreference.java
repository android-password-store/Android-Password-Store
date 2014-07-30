package com.zeapo.pwdstore;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class UserPreference extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
    }
}
