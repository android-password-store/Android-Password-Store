package com.zeapo.pwdstore.git;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.UserPreference;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO move the messages to strings.xml

public class GitActivity extends AppCompatActivity {
    private static final String TAG = "GitAct";
    // copied from http://stackoverflow.com/a/16058059/4247851
    private static final String emailPattern = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$";

    private Activity activity;
    private Context context;

    private String protocol;
    private String connectionMode;

    private File localDir;
    private String hostname;
    private String port;

    private SharedPreferences settings;

    public static final int REQUEST_PULL = 101;
    public static final int REQUEST_PUSH = 102;
    public static final int REQUEST_CLONE = 103;
    public static final int REQUEST_INIT = 104;
    public static final int EDIT_SERVER = 105;
    public static final int REQUEST_SYNC = 106;
    public static final int REQUEST_CREATE = 107;
    public static final int EDIT_GIT_CONFIG = 108;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getApplicationContext();
        activity = this;

        settings = PreferenceManager.getDefaultSharedPreferences(this.context);

        protocol = settings.getString("git_remote_protocol", "ssh://");
        connectionMode = settings.getString("git_remote_auth", "ssh-key");
        int operationCode = getIntent().getExtras().getInt("Operation");

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        switch (operationCode) {
            case REQUEST_CLONE:
            case EDIT_SERVER:
                setContentView(R.layout.activity_git_clone);
                setTitle(R.string.title_activity_git_clone);

                final Spinner protcol_spinner = (Spinner) findViewById(R.id.clone_protocol);
                final Spinner connection_mode_spinner = (Spinner) findViewById(R.id.connection_mode);

                // init the spinner for connection modes
                final ArrayAdapter<CharSequence> connection_mode_adapter = ArrayAdapter.createFromResource(this,
                        R.array.connection_modes, android.R.layout.simple_spinner_item);
                connection_mode_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                connection_mode_spinner.setAdapter(connection_mode_adapter);
                connection_mode_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                        String selection = ((Spinner) findViewById(R.id.connection_mode)).getSelectedItem().toString();
                        connectionMode = selection;
                        settings.edit().putString("git_remote_auth", selection).apply();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {

                    }
                });

                // init the spinner for protocols
                ArrayAdapter<CharSequence> protocol_adapter = ArrayAdapter.createFromResource(this,
                        R.array.clone_protocols, android.R.layout.simple_spinner_item);
                protocol_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                protcol_spinner.setAdapter(protocol_adapter);
                protcol_spinner.setOnItemSelectedListener(
                        new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                                protocol = ((Spinner) findViewById(R.id.clone_protocol)).getSelectedItem().toString();
                                if (protocol.equals("ssh://")) {
                                    ((EditText) findViewById(R.id.clone_uri)).setHint("user@hostname:path");

                                    ((EditText) findViewById(R.id.server_port)).setHint(R.string.default_ssh_port);

                                    // select ssh-key auth mode as default and enable the spinner in case it was disabled
                                    connection_mode_spinner.setSelection(0);
                                    connection_mode_spinner.setEnabled(true);

                                    // however, if we have some saved that, that's more important!
                                    if (connectionMode.equals("ssh-key")) {
                                        connection_mode_spinner.setSelection(0);
                                    } else {
                                        connection_mode_spinner.setSelection(1);
                                    }
                                } else {
                                    ((EditText) findViewById(R.id.clone_uri)).setHint("hostname/path");

                                    ((EditText) findViewById(R.id.server_port)).setHint(R.string.default_https_port);

                                    // select user/pwd auth-mode and disable the spinner
                                    connection_mode_spinner.setSelection(1);
                                    connection_mode_spinner.setEnabled(false);
                                }
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> adapterView) {

                            }
                        }
                );

                if (protocol.equals("ssh://")) {
                    protcol_spinner.setSelection(0);
                } else {
                    protcol_spinner.setSelection(1);
                }

                // init the server information
                final EditText server_url = ((EditText) findViewById(R.id.server_url));
                final EditText server_port = ((EditText) findViewById(R.id.server_port));
                final EditText server_path = ((EditText) findViewById(R.id.server_path));
                final EditText server_user = ((EditText) findViewById(R.id.server_user));
                final EditText server_uri = ((EditText) findViewById(R.id.clone_uri));

                View.OnFocusChangeListener updateListener = new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean b) {
                        updateURI();
                    }
                };

                server_url.setText(settings.getString("git_remote_server", ""));
                server_port.setText(settings.getString("git_remote_port", ""));
                server_user.setText(settings.getString("git_remote_username", ""));
                server_path.setText(settings.getString("git_remote_location", ""));

                server_url.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        if (server_url.isFocused())
                            updateURI();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                    }
                });
                server_port.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        if (server_port.isFocused())
                            updateURI();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                    }
                });
                server_user.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        if (server_user.isFocused())
                            updateURI();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                    }
                });
                server_path.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        if (server_path.isFocused())
                            updateURI();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                    }
                });

                server_uri.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        if (server_uri.isFocused())
                            splitURI();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                    }
                });

                if (operationCode == EDIT_SERVER) {
                    findViewById(R.id.clone_button).setVisibility(View.INVISIBLE);
                    findViewById(R.id.save_button).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.clone_button).setVisibility(View.VISIBLE);
                    findViewById(R.id.save_button).setVisibility(View.INVISIBLE);
                }

                updateURI();

                break;
            case EDIT_GIT_CONFIG:
                setContentView(R.layout.activity_git_config);
                setTitle(R.string.title_activity_git_config);

                // init the server information
                final EditText git_user_name = ((EditText) findViewById(R.id.git_user_name));
                final EditText git_user_email = ((EditText) findViewById(R.id.git_user_email));

                git_user_name.setText(settings.getString("git_config_user_name", ""));
                git_user_email.setText(settings.getString("git_config_user_email", ""));

                break;
            case REQUEST_PULL:
                syncRepository(REQUEST_PULL);
                break;

            case REQUEST_PUSH:
                syncRepository(REQUEST_PUSH);
                break;

            case REQUEST_SYNC:
                syncRepository(REQUEST_SYNC);
                break;
        }


    }

    /**
     * Fills in the server_uri field with the information coming from other fields
     */
    private void updateURI() {
        EditText uri = (EditText) findViewById(R.id.clone_uri);
        EditText server_url = ((EditText) findViewById(R.id.server_url));
        EditText server_port = ((EditText) findViewById(R.id.server_port));
        EditText server_path = ((EditText) findViewById(R.id.server_path));
        EditText server_user = ((EditText) findViewById(R.id.server_user));

        if (uri != null) {
            switch (protocol) {
                case "ssh://": {
                    String hostname =
                            server_user.getText()
                                    + "@" +
                                    server_url.getText().toString().trim()
                                    + ":";
                    if (server_port.getText().toString().equals("22")) {
                        hostname += server_path.getText().toString();

                        ((TextView) findViewById(R.id.warn_url)).setVisibility(View.GONE);
                    } else {
                        TextView warn_url = (TextView) findViewById(R.id.warn_url);
                        if (!server_path.getText().toString().matches("/.*") && !server_port.getText().toString().isEmpty()) {
                            warn_url.setText(R.string.warn_malformed_url_port);
                            warn_url.setVisibility(View.VISIBLE);
                        } else {
                            warn_url.setVisibility(View.GONE);
                        }
                        hostname += server_port.getText().toString() + server_path.getText().toString();
                    }

                    if (!hostname.equals("@:")) uri.setText(hostname);
                }
                break;
                case "https://": {
                    StringBuilder hostname = new StringBuilder();
                    hostname.append(server_url.getText().toString().trim());

                    if (server_port.getText().toString().equals("443")) {
                        hostname.append(server_path.getText().toString());

                        ((TextView) findViewById(R.id.warn_url)).setVisibility(View.GONE);
                    } else {
                        hostname.append("/");
                        hostname.append(server_port.getText().toString())
                                .append(server_path.getText().toString());
                    }

                    if (!hostname.toString().equals("@/")) uri.setText(hostname);
                }
                break;
                default:
                    break;
            }

        }
    }

    /**
     * Splits the information in server_uri into the other fields
     */
    private void splitURI() {
        EditText server_uri = (EditText) findViewById(R.id.clone_uri);
        EditText server_url = ((EditText) findViewById(R.id.server_url));
        EditText server_port = ((EditText) findViewById(R.id.server_port));
        EditText server_path = ((EditText) findViewById(R.id.server_path));
        EditText server_user = ((EditText) findViewById(R.id.server_user));

        String uri = server_uri.getText().toString();
        Pattern pattern = Pattern.compile("(.+)@([\\w\\d\\.]+):([\\d]+)*(.*)");
        Matcher matcher = pattern.matcher(uri);
        if (matcher.find()) {
            int count = matcher.groupCount();
            if (count > 1) {
                server_user.setText(matcher.group(1));
                server_url.setText(matcher.group(2));
            }
            if (count == 4) {
                server_port.setText(matcher.group(3));
                server_path.setText(matcher.group(4));

                TextView warn_url = (TextView) findViewById(R.id.warn_url);
                if (!server_path.getText().toString().matches("/.*") && !server_port.getText().toString().isEmpty()) {
                    warn_url.setText(R.string.warn_malformed_url_port);
                    warn_url.setVisibility(View.VISIBLE);
                } else {
                    warn_url.setVisibility(View.GONE);
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateURI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.git_clone, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.user_pref:
                try {
                    Intent intent = new Intent(this, UserPreference.class);
                    startActivity(intent);
                } catch (Exception e) {
                    System.out.println("Exception caught :(");
                    e.printStackTrace();
                }
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Saves the configuration found in the form
     */
    private boolean saveConfiguration() {
        // remember the settings
        SharedPreferences.Editor editor = settings.edit();

        editor.putString("git_remote_server", ((EditText) findViewById(R.id.server_url)).getText().toString());
        editor.putString("git_remote_location", ((EditText) findViewById(R.id.server_path)).getText().toString());
        editor.putString("git_remote_username", ((EditText) findViewById(R.id.server_user)).getText().toString());
        editor.putString("git_remote_protocol", protocol);
        editor.putString("git_remote_auth", connectionMode);
        editor.putString("git_remote_port", ((EditText) findViewById(R.id.server_port)).getText().toString());
        editor.putString("git_remote_uri", ((EditText) findViewById(R.id.clone_uri)).getText().toString());

        // 'save' hostname variable for use by addRemote() either here or later
        // in syncRepository()
        hostname = ((EditText) findViewById(R.id.clone_uri)).getText().toString();
        port = ((EditText) findViewById(R.id.server_port)).getText().toString();
        // don't ask the user, take off the protocol that he puts in
        hostname = hostname.replaceFirst("^.+://", "");
        ((TextView) findViewById(R.id.clone_uri)).setText(hostname);

        if (!protocol.equals("ssh://")) {
            hostname = protocol + hostname;
        } else {
            // if the port is explicitly given, jgit requires the ssh://
            if (!port.isEmpty() && !port.equals("22"))
                hostname = protocol + hostname;

            // did he forget the username?
            if (!hostname.matches("^.+@.+")) {
                new AlertDialog.Builder(this).
                        setMessage(activity.getResources().getString(R.string.forget_username_dialog_text)).
                        setPositiveButton(activity.getResources().getString(R.string.dialog_oops), null).
                        show();
                return false;
            }
        }
        if (PasswordRepository.isInitialized()) {
            // don't just use the clone_uri text, need to use hostname which has
            // had the proper protocol prepended
            PasswordRepository.addRemote("origin", hostname, true);
        }

        editor.apply();
        return true;
    }

    /**
     * Save the repository information to the shared preferences settings
     *
     * @param view
     */
    public void saveConfiguration(View view) {
        if (!saveConfiguration())
            return;
        finish();
    }

    private boolean saveGitConfigs() {
        // remember the settings
        SharedPreferences.Editor editor = settings.edit();

        String email = ((EditText) findViewById(R.id.git_user_email)).getText().toString();
        editor.putString("git_config_user_email", email);
        editor.putString("git_config_user_name", ((EditText) findViewById(R.id.git_user_name)).getText().toString());

        if (!email.matches(emailPattern)) {
            new AlertDialog.Builder(this).
                setMessage(activity.getResources().getString(R.string.invalid_email_dialog_text)).
                setPositiveButton(activity.getResources().getString(R.string.dialog_oops), null).
                show();
            return false;
        }

        editor.apply();
        return true;
    }

    public void applyGitConfigs(View view) {
        if(!saveGitConfigs())
            return;

        String git_user_name = settings.getString("git_config_user_name", "");
        String git_user_email = settings.getString("git_config_user_email", "");

        PasswordRepository.setUserName(git_user_name);
        PasswordRepository.setUserEmail(git_user_email);

        finish();
    }

    /**
     * Clones the repository, the directory exists, deletes it
     *
     * @param view
     */
    public void cloneRepository(View view) {
        if (PasswordRepository.getRepository(null) == null) {
            PasswordRepository.initialize(this);
        }
        localDir = PasswordRepository.getRepositoryDirectory(context);

        if (!saveConfiguration())
            return;

        // Warn if non-empty folder unless it's a just-initialized store that has just a .git folder
        if (localDir.exists() && localDir.listFiles().length != 0
                && !(localDir.listFiles().length == 1 && localDir.listFiles()[0].getName().equals(".git"))) {
            new AlertDialog.Builder(this).
                    setTitle(R.string.dialog_delete_title).
                    setMessage(getResources().getString(R.string.dialog_delete_msg) + " " + localDir.toString()).
                    setCancelable(false).
                    setPositiveButton(R.string.dialog_delete,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    try {
                                        FileUtils.deleteDirectory(localDir);
                                        try {
                                            new CloneOperation(localDir, activity)
                                                    .setCommand(hostname)
                                                    .executeAfterAuthentication(connectionMode, settings.getString("git_remote_username", "git"), new File(getFilesDir() + "/.ssh_key"));
                                        } catch (Exception e) {
                                            //This is what happens when jgit fails :(
                                            //TODO Handle the diffent cases of exceptions
                                            e.printStackTrace();
                                            new AlertDialog.Builder(GitActivity.this).setMessage(e.getMessage()).show();
                                        }
                                    } catch (IOException e) {
                                        //TODO Handle the exception correctly if we are unable to delete the directory...
                                        e.printStackTrace();
                                        new AlertDialog.Builder(GitActivity.this).setMessage(e.getMessage()).show();
                                    }

                                    dialog.cancel();
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
        } else {
            try {
                // Silently delete & replace the lone .git folder if it exists
                if (localDir.listFiles().length == 1 && localDir.listFiles()[0].getName().equals(".git")) {
                    try {
                        FileUtils.deleteDirectory(localDir);
                    } catch (IOException e) {
                        e.printStackTrace();
                        new AlertDialog.Builder(GitActivity.this).setMessage(e.getMessage()).show();
                    }
                }
                new CloneOperation(localDir, activity)
                        .setCommand(hostname)
                        .executeAfterAuthentication(connectionMode, settings.getString("git_remote_username", "git"), new File(getFilesDir() + "/.ssh_key"));
            } catch (Exception e) {
                //This is what happens when jgit fails :(
                //TODO Handle the diffent cases of exceptions
                e.printStackTrace();
                new AlertDialog.Builder(this).setMessage(e.getMessage()).show();
            }
        }
    }

    /**
     * Syncs the local repository with the remote one (either pull or push)
     * @param operation the operation to execute can be REQUEST_PULL or REQUEST_PUSH
     */
    private void syncRepository(int operation) {
        if (settings.getString("git_remote_username", "").isEmpty() ||
                settings.getString("git_remote_server", "").isEmpty() ||
                settings.getString("git_remote_location", "").isEmpty())
            new AlertDialog.Builder(this)
                    .setMessage(activity.getResources().getString(R.string.set_information_dialog_text))
                    .setPositiveButton(activity.getResources().getString(R.string.dialog_positive), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(activity, UserPreference.class);
                            startActivityForResult(intent, REQUEST_PULL);
                        }
                    })
                    .setNegativeButton(activity.getResources().getString(R.string.dialog_negative), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            // do nothing :(
                            setResult(RESULT_OK);
                            finish();
                        }
                    })
                    .show();

        else {
            // check that the remote origin is here, else add it
            PasswordRepository.addRemote("origin", hostname, false);
            GitOperation op;

            switch (operation) {
                case REQUEST_PULL:
                    op = new PullOperation(localDir, activity).setCommand();
                    break;
                case REQUEST_PUSH:
                    op = new PushOperation(localDir, activity).setCommand();
                    break;
                case REQUEST_SYNC:
                    op = new SyncOperation(localDir, activity).setCommands();
                    break;
                default:
                    Log.e(TAG, "Sync operation not recognized : " + operation);
                    return;
            }

            try {
                op.executeAfterAuthentication(connectionMode, settings.getString("git_remote_username", "git"), new File(getFilesDir() + "/.ssh_key"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        if (resultCode == RESULT_OK) {
            GitOperation op;

            switch (requestCode) {
                case REQUEST_CLONE:
                    setResult(RESULT_OK);
                    finish();
                    return;
                case REQUEST_PULL:
                    op = new PullOperation(localDir, activity).setCommand();
                    break;

                case REQUEST_PUSH:
                    op = new PushOperation(localDir, activity).setCommand();
                    break;

                case GitOperation.GET_SSH_KEY_FROM_CLONE:
                    op = new CloneOperation(localDir, activity).setCommand(hostname);
                    break;
                default:
                    Log.e(TAG, "Operation not recognized : " + resultCode);
                    setResult(RESULT_CANCELED);
                    finish();
                    return;
            }

            try {
                op.executeAfterAuthentication(connectionMode, settings.getString("git_remote_username", "git"), new File(getFilesDir() + "/.ssh_key"));
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

}
