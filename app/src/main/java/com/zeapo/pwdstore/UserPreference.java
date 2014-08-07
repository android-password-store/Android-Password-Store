package com.zeapo.pwdstore;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.zeapo.pwdstore.crypto.PgpHandler;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.apache.commons.io.FileUtils;

public class UserPreference extends PreferenceActivity implements Preference.OnPreferenceClickListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preference);
        findPreference("openpgp_key_id").setOnPreferenceClickListener(this);

        getActionBar().setDisplayHomeAsUpEnabled(true);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                setResult(RESULT_OK);
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        System.out.println(pref);
        if (pref.getKey().equals("openpgp_key_id")) {
            Intent intent = new Intent(this, PgpHandler.class);
            intent.putExtra("Operation", "GET_KEY_ID");
            startActivityForResult(intent, 0);
        }
        return true;
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
        }
    }
}
