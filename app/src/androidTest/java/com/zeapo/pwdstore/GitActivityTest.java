package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;
import android.widget.EditText;
import android.widget.Spinner;

import com.zeapo.pwdstore.git.GitActivity;

public class GitActivityTest extends ActivityInstrumentationTestCase2<GitActivity> {
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

    public GitActivityTest() {
        super(GitActivity.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();

        Intent intent = new Intent();
        intent.putExtra("Operation", GitActivity.EDIT_SERVER);
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

        assertEquals(protocolSpinner.getSelectedItem(), settings.getString("git_remote_protocol", "ssh://"));
        assertEquals(connectionModeSpinner.getSelectedItem(), settings.getString("git_remote_auth", "ssh-key"));
    }

    /**
     * If we change from ssh protocol to https we automatically switch to username/password auth
     * @throws Exception
     */
    public void testSpinnerChange() throws Exception{
        gitActivity.runOnUiThread(new Runnable() {
            public void run() {
                protocolSpinner.requestFocus();
                protocolSpinner.setSelection(1); // 1 < is https://
            }
        });
        mInstrumentation.waitForIdleSync();

        assertEquals(connectionModeSpinner.getSelectedItem(), "username/password"); // 1 < is username/password
    }
}
