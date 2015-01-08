package com.zeapo.pwdstore;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.zeapo.pwdstore.crypto.PgpHandler;
import com.zeapo.pwdstore.git.GitActivity;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class UserPreference extends ActionBarActivity implements Preference.OnPreferenceClickListener {
    private final static int IMPORT_SSH_KEY = 1;
    private final static int IMPORT_PGP_KEY = 2;
    private final static int EDIT_GIT_INFO = 3;

    public static class PrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preference);
            findPreference("openpgp_key_id").setOnPreferenceClickListener((UserPreference) getActivity());
            findPreference("ssh_key").setOnPreferenceClickListener((UserPreference) getActivity());
            findPreference("git_server_info").setOnPreferenceClickListener((UserPreference) getActivity());
            findPreference("git_delete_repo").setOnPreferenceClickListener((UserPreference) getActivity());
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null) {
            if ((getIntent().getStringExtra("operation") != null) && (getIntent().getStringExtra("operation").equals("get_ssh_key"))) {
                getSshKey();
            }
        }

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefsFragment()).commit();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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

    /**
     * Opens a file explorer to import the private key
     */
    public void getSshKey() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, IMPORT_SSH_KEY);
    }


    private void copySshKey(Uri uri) throws IOException {
        InputStream sshKey = this.getContentResolver().openInputStream(uri);
        byte[] privateKey = IOUtils.toByteArray(sshKey);
        FileUtils.writeByteArrayToFile(new File(getFilesDir() + "/.ssh_key"), privateKey);
        sshKey.close();
    }

    @Override
    public boolean onPreferenceClick(Preference pref) {
        switch (pref.getKey())
        {
            case "openpgp_key_id":
            {
                Intent intent = new Intent(this, PgpHandler.class);
                intent.putExtra("Operation", "GET_KEY_ID");
                startActivityForResult(intent, IMPORT_PGP_KEY);
            }
            break;
            case "ssh_key":
            {
                getSshKey();
            }
            break;
            case "git_server_info":
            {
                Intent intent = new Intent(this, GitActivity.class);
                intent.putExtra("Operation", GitActivity.EDIT_SERVER);
                startActivityForResult(intent, EDIT_GIT_INFO);
            }
            break;
            case "git_delete_repo":
            {
                new AlertDialog.Builder(this).
                        setTitle(R.string.pref_dialog_delete_title).
                        setMessage(R.string.pref_dialog_delete_msg).
                        setCancelable(false).
                        setPositiveButton(R.string.dialog_delete,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        try {
                                            FileUtils.deleteDirectory(PasswordRepository.getWorkTree());
                                        } catch (Exception e) {
                                            //TODO Handle the diffent cases of exceptions
                                        }

                                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putBoolean("repository_initialized", false).commit();
                                        dialog.cancel();
                                        finish();
                                    }
                                }
                        ).
                        setNegativeButton(R.string.dialog_do_not_delete,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                }
                        ).
                        show();
            }
        }
        return true;
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode)
            {
                case IMPORT_SSH_KEY:
                {
                    try {
                        copySshKey(data.getData());
                        Toast.makeText(this, this.getResources().getString(R.string.ssh_key_success_dialog_title), Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    } catch (IOException e)
                    {
                        new AlertDialog.Builder(this).
                                setTitle(this.getResources().getString(R.string.ssh_key_error_dialog_title)).
                                setMessage(this.getResources().getString(R.string.ssh_key_error_dialog_text) + e.getMessage()).
                                setPositiveButton(this.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        //pass
                                    }
                                }).show();
                    }
                }
                break;
                case EDIT_GIT_INFO:
                {

                }
                break;
                default:
                break;
            }
        }
    }
}
