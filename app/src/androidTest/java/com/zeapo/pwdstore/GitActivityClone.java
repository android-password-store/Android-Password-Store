package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.robotium.solo.Solo;
import com.zeapo.pwdstore.git.GitActivity;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.apache.commons.io.FileUtils;

import java.io.File;

public class GitActivityClone extends ActivityInstrumentationTestCase2<GitActivity> {
    private static final String TAG = "GitActTest";
    private Activity gitActivity;
    private Instrumentation mInstrumentation;
    private SharedPreferences settings;

    private Spinner protocolSpinner;
    private Spinner connectionModeSpinner;
    private EditText uri;
    private EditText server_url;
    private EditText server_port;
    private EditText server_path;
    private EditText server_user;

    public GitActivityClone() {
        super(GitActivity.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();

        Intent intent = new Intent();
        intent.putExtra("Operation", GitActivity.REQUEST_CLONE);
        setActivityIntent(intent);

        gitActivity = getActivity(); // get a references to the app under test
        assertNotNull(gitActivity);

        settings = PreferenceManager.getDefaultSharedPreferences(gitActivity.getApplicationContext());

        uri = (EditText) gitActivity.findViewById(R.id.clone_uri);
        server_url = ((EditText) gitActivity.findViewById(R.id.server_url));
        server_port = ((EditText) gitActivity.findViewById(R.id.server_port));
        server_path = ((EditText) gitActivity.findViewById(R.id.server_path));
        server_user = ((EditText) gitActivity.findViewById(R.id.server_user));
        protocolSpinner = (Spinner) gitActivity.findViewById(R.id.clone_protocol);
        connectionModeSpinner = (Spinner) gitActivity.findViewById(R.id.connection_mode);

        assertNotNull(uri);
        assertNotNull(server_url);
        assertNotNull(server_port);
        assertNotNull(server_path);
        assertNotNull(server_user);
        assertNotNull(protocolSpinner);
        assertNotNull(connectionModeSpinner);

        assertEquals(protocolSpinner.getSelectedItem(), settings.getString("git_remote_protocol", "ssh://"));
        assertEquals(connectionModeSpinner.getSelectedItem(), settings.getString("git_remote_auth", "ssh-key"));
    }

    public void testCloneSshUser() throws Exception {
        final Solo solo = new Solo(getInstrumentation(), getActivity());
        FileUtils.deleteDirectory(new File(gitActivity.getFilesDir() +  gitActivity.getResources().getString(R.string.store_git)));
        // create the repository static variable in PasswordRepository
        PasswordRepository.getRepository(new File(gitActivity.getFilesDir() + gitActivity.getResources().getString(R.string.store_git)));

        gitActivity.runOnUiThread(new Runnable() {
            public void run() {
                protocolSpinner.setSelection(0); // ssh://
            }
        });

        mInstrumentation.waitForIdleSync();

        solo.clearEditText(server_user);
        solo.enterText(server_user, "testpwd");
        solo.clearEditText(server_path);
        solo.enterText(server_path, "repo-test");
        solo.clearEditText(server_url);
        solo.enterText(server_url, "192.168.1.28");

        mInstrumentation.waitForIdleSync();

        gitActivity.runOnUiThread(new Runnable() {
            public void run() {
                connectionModeSpinner.setSelection(1); // user/pwd
                ((Button) gitActivity.findViewById(R.id.clone_button)).performClick();
            }
        });

        mInstrumentation.waitForIdleSync();

        assertTrue("Could not find the dialog!", solo.searchText(gitActivity.getResources().getString(R.string.passphrase_dialog_title)));
        solo.enterText(solo.getEditText("Password"), "test");
        solo.clickOnButton(gitActivity.getResources().getString(R.string.dialog_ok));

        mInstrumentation.waitForIdleSync();
    }
}
