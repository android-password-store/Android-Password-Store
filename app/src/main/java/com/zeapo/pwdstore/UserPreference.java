package com.zeapo.pwdstore;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.zeapo.pwdstore.crypto.PgpHandler;
import com.zeapo.pwdstore.git.GitActivity;
import com.zeapo.pwdstore.utils.PasswordRepository;

import net.rdrei.android.dirchooser.DirectoryChooserActivity;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.openintents.openpgp.util.OpenPgpKeyPreference;
import org.openintents.openpgp.util.OpenPgpUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class UserPreference extends AppCompatActivity {
    private final static int IMPORT_SSH_KEY = 1;
    private final static int IMPORT_PGP_KEY = 2;
    private final static int EDIT_GIT_INFO = 3;
    private OpenPgpKeyPreference mKey;
    private final static int SELECT_GIT_DIRECTORY = 4;

    public static class PrefsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final UserPreference callingActivity = (UserPreference) getActivity();
            final SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();

            Preference keyPref = findPreference("openpgp_key_id_pref");
            String selectedKeys = sharedPreferences.getString("openpgp_key_ids", "");
            if (Strings.isNullOrEmpty(selectedKeys)) {
                keyPref.setSummary("No key selected");
            } else {
                keyPref.setSummary(
                        Joiner.on(',')
                        .join(Iterables.transform(Arrays.asList(selectedKeys.split(",")), input -> OpenPgpUtils.convertKeyIdToHex(Long.valueOf(input))))
                );
            }
            keyPref.setOnPreferenceClickListener((Preference pref) -> {
                Intent intent = new Intent(callingActivity, PgpHandler.class);
                intent.putExtra("Operation", "GET_KEY_ID");
                startActivityForResult(intent, IMPORT_PGP_KEY);
                return true;
            });

            findPreference("ssh_key").setOnPreferenceClickListener((Preference pref) -> {
                callingActivity.getSshKey();
                return true;
            });

            findPreference("git_server_info").setOnPreferenceClickListener((Preference pref) -> {
                Intent intent = new Intent(callingActivity, GitActivity.class);
                intent.putExtra("Operation", GitActivity.EDIT_SERVER);
                startActivityForResult(intent, EDIT_GIT_INFO);
                return true;
            });

            findPreference("git_delete_repo").setOnPreferenceClickListener((Preference pref) -> {
                new AlertDialog.Builder(callingActivity).
                        setTitle(R.string.pref_dialog_delete_title).
                        setMessage(R.string.pref_dialog_delete_msg).
                        setCancelable(false).
                        setPositiveButton(R.string.dialog_delete,
                                (dialog, id) -> {
                                    try {
                                        FileUtils.cleanDirectory(PasswordRepository.getWorkTree());
                                    } catch (Exception e) {
                                        //TODO Handle the diffent cases of exceptions
                                    }

                                    sharedPreferences.edit().putBoolean("repository_initialized", false).commit();
                                    dialog.cancel();
                                    callingActivity.finish();
                                }
                        ).
                        setNegativeButton(R.string.dialog_do_not_delete,
                                (dialog, id) -> {
                                    dialog.cancel();
                                }
                        ).
                        show();
                return true;
            });

            callingActivity.mKey = (OpenPgpKeyPreference) findPreference("openpgp_key");
            if (sharedPreferences.getString("openpgp_provider_list", null) != null)
                ((UserPreference) getActivity()).mKey.setOpenPgpProvider(sharedPreferences.getString("openpgp_provider_list", ""));

            findPreference("openpgp_provider_list").setOnPreferenceChangeListener((preference, o) -> {
                callingActivity.mKey.setOpenPgpProvider((String) o);
                return false;
            });

            findPreference("pref_select_external").setOnPreferenceClickListener((Preference pref) -> {
                callingActivity.selectExternalGitRepository();
                return true;
            });
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() != null) {
            if (getIntent().getStringExtra("operation") != null) {
                switch (getIntent().getStringExtra("operation")) {
                    case "get_ssh_key":
                        getSshKey();
                        break;
                    case "git_external":
                        selectExternalGitRepository();
                        break;
                }
            }
        }

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefsFragment()).commit();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    public void selectExternalGitRepository() {
        Intent intent = new Intent(this, DirectoryChooserActivity.class);
        intent.putExtra(DirectoryChooserActivity.EXTRA_NEW_DIR_NAME,
                "passwordstore");

        startActivityForResult(intent, SELECT_GIT_DIRECTORY);
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

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode)
            {
                case IMPORT_SSH_KEY:
                {
                    try {
                        if (data.getData() == null) {
                            throw new IOException("Unable to open file");
                        }
                        copySshKey(data.getData());
                        Toast.makeText(this, this.getResources().getString(R.string.ssh_key_success_dialog_title), Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    } catch (IOException e)
                    {
                        new AlertDialog.Builder(this).
                                setTitle(this.getResources().getString(R.string.ssh_key_error_dialog_title)).
                                setMessage(this.getResources().getString(R.string.ssh_key_error_dialog_text) + e.getMessage()).
                                setPositiveButton(this.getResources().getString(R.string.dialog_ok), (dialogInterface, i) -> {
                                    //pass
                                }).show();
                    }
                }
                break;
                case EDIT_GIT_INFO:
                {

                }
                break;
                case OpenPgpKeyPreference.REQUEST_CODE_KEY_PREFERENCE:
                {
                    if (mKey.handleOnActivityResult(requestCode, resultCode, data)) {
                        // handled by OpenPgpKeyPreference
                        PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext()).edit().putLong("openpgp_sign_key", mKey.getValue()).apply();
                        return;
                    }
                }
                break;
                default:
                break;
            }
        }

        // why do they have to use a different resultCode than OK :/
        if (requestCode == SELECT_GIT_DIRECTORY && resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
            PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                    .edit()
                    .putString("git_external_repo", data.getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR))
                    .commit();
        }
    }
}
