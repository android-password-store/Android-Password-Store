package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProviderUserInfo;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO move the messages to strings.xml

public class GitHandler extends ActionBarActivity {

    private Activity activity;
    private Context context;

    private String protocol;
    private String connectionMode;

    private File localDir;
    private String hostname;
    private String username;
    private String port;

    private SharedPreferences settings;

    public static final int REQUEST_PULL = 101;
    public static final int REQUEST_PUSH = 102;
    public static final int REQUEST_CLONE = 103;
    public static final int REQUEST_INIT = 104;

    private static final int GET_SSH_KEY_FROM_CLONE = 201;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getApplicationContext();
        activity = this;

        settings = PreferenceManager.getDefaultSharedPreferences(this.context);

        protocol = settings.getString("git_remote_protocol", "ssh://");
        connectionMode = settings.getString("git_remote_auth", "ssh-key");

        switch (getIntent().getExtras().getInt("Operation")) {
            case REQUEST_CLONE:
                setContentView(R.layout.activity_git_clone);

                // init the spinner for protocols
                Spinner protcol_spinner = (Spinner) findViewById(R.id.clone_protocol);
                ArrayAdapter<CharSequence> protocol_adapter = ArrayAdapter.createFromResource(this,
                        R.array.clone_protocols, android.R.layout.simple_spinner_item);
                protocol_adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                protcol_spinner.setAdapter(protocol_adapter);
                protcol_spinner.setOnItemSelectedListener(
                        new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                                protocol = ((Spinner)findViewById(R.id.clone_protocol)).getSelectedItem().toString();
                                if (protocol.equals("ssh://")) {
                                    ((EditText)findViewById(R.id.clone_uri)).setHint("user@hostname:path");
                                } else {
                                    ((EditText)findViewById(R.id.clone_uri)).setHint("hostname/path");
                                    new AlertDialog.Builder(activity).
                                            setMessage("You are about to use a read-only repository, you will not be able to push to it").
                                            setCancelable(true).
                                            setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {

                                                }
                                            }).show();
                                }
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> adapterView) {

                            }
                        }
                );

                // init the spinner for connection modes
                Spinner connection_mode_spinner = (Spinner) findViewById(R.id.connection_mode);
                ArrayAdapter<CharSequence> connection_mode_adapter = ArrayAdapter.createFromResource(this,
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

                // init the server information
                final EditText server_url = ((EditText) findViewById(R.id.server_url));
                final EditText server_port = ((EditText) findViewById(R.id.server_port));
                final EditText server_path = ((EditText) findViewById(R.id.server_path));
                final EditText server_user = ((EditText) findViewById(R.id.server_user));
                final EditText server_uri = ((EditText)findViewById(R.id.clone_uri));

                View.OnFocusChangeListener updateListener = new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean b) {
                        updateURI();
                    }
                };

                server_url.setText(settings.getString("git_remote_server", ""));
                server_port.setText(settings.getString("git_remote_server_port", ""));
                server_user.setText(settings.getString("git_remote_username", ""));
                server_path.setText(settings.getString("git_remote_location", ""));

                server_url.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) { }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        if (server_url.isFocused())
                            updateURI();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) { }
                });
                server_port.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) { }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        if (server_port.isFocused())
                            updateURI();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) { }
                });
                server_user.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) { }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        if (server_user.isFocused())
                            updateURI();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) { }
                });
                server_path.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) { }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        if (server_path.isFocused())
                            updateURI();
                    }

                    @Override
                    public void afterTextChanged(Editable editable) { }
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

                break;
            case REQUEST_PULL:
                authenticateAndRun("pullOperation");
                break;

            case REQUEST_PUSH:
                authenticateAndRun("pushOperation");
                break;
        }


    }

    /** Fills in the server_uri field with the information coming from other fields */
    private void updateURI() {
        EditText uri = (EditText) findViewById(R.id.clone_uri);
        EditText server_url = ((EditText) findViewById(R.id.server_url));
        EditText server_port = ((EditText) findViewById(R.id.server_port));
        EditText server_path = ((EditText) findViewById(R.id.server_path));
        EditText server_user = ((EditText) findViewById(R.id.server_user));
        Log.i("GIT", "key entred");

        if (uri != null) {
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
    }

    /** Splits the information in server_uri into the other fields */
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
            Log.i("GIT", ">> " + count);
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

    protected class GitConfigSessionFactory extends JschConfigSessionFactory {

        protected void configure(OpenSshConfig.Host hc, Session session) {
            session.setConfig("StrictHostKeyChecking", "no");
        }

        @Override
        protected JSch
        getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException {
            JSch jsch = super.getJSch(hc, fs);
            jsch.removeAllIdentity();
            return jsch;
        }
    }

    protected class SshConfigSessionFactory extends GitConfigSessionFactory {
        private String sshKey;
        private String passphrase;

        public SshConfigSessionFactory(String sshKey, String passphrase) {
            this.sshKey = sshKey;
            this.passphrase = passphrase;
        }

        @Override
        protected JSch
        getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException {
            JSch jsch = super.getJSch(hc, fs);
            jsch.removeAllIdentity();
            jsch.addIdentity(sshKey);
            return jsch;
        }

        @Override
        protected void configure(OpenSshConfig.Host hc, Session session) {
            session.setConfig("StrictHostKeyChecking", "no");

            CredentialsProvider provider = new CredentialsProvider() {
                @Override
                public boolean isInteractive() {
                    return false;
                }

                @Override
                public boolean supports(CredentialItem... items) {
                    return true;
                }

                @Override
                public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
                    for (CredentialItem item : items) {
                        if (item instanceof CredentialItem.Username) {
                            ((CredentialItem.Username) item).setValue(settings.getString("git_remote_username", "git"));
                            continue;
                        }
                        if (item instanceof CredentialItem.StringType) {
                            ((CredentialItem.StringType) item).setValue(passphrase);
                        }
                    }
                    return true;
                }
            };
            UserInfo userInfo = new CredentialsProviderUserInfo(session, provider);
            session.setUserInfo(userInfo);
        }
    }


    public void cloneRepository(View view) {
        localDir = new File(getApplicationContext().getFilesDir().getAbsoluteFile() + "/store");

        hostname = ((EditText) findViewById(R.id.clone_uri)).getText().toString();
        port = ((EditText) findViewById(R.id.server_port)).getText().toString();
        // don't ask the user, take off the protocol that he puts in
        hostname = hostname.replaceFirst("^.+://", "");
        ((TextView) findViewById(R.id.clone_uri)).setText(hostname);

        // now cheat a little and prepend the real protocol
        // jGit does not accept a ssh:// but requires https://
        if (!protocol.equals("ssh://")) {
            hostname = protocol + hostname;
        } else {

            // if the port is explicitly given, jgit requires the ssh://
            if (!port.isEmpty())
                hostname = protocol + hostname;

            Log.i("GIT", "> " + port);

            // did he forget the username?
            if (!hostname.matches("^.+@.+")) {
                new AlertDialog.Builder(this).
                        setMessage("Did you forget to specify a username?").
                        setPositiveButton("Oups...", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        }).
                        show();
                return;
            }

            username = hostname.split("@")[0];
        }


        if (localDir.exists()) {
            new AlertDialog.Builder(this).
                    setTitle(R.string.dialog_delete_title).
                    setMessage(R.string.dialog_delete_msg).
                    setCancelable(false).
                    setPositiveButton(R.string.dialog_delete,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    try {
                                        FileUtils.deleteDirectory(localDir);
                                        authenticateAndRun("cloneOperation");
                                    } catch (IOException e) {
                                        //TODO Handle the exception correctly if we are unable to delete the directory...
                                        e.printStackTrace();
                                    } catch (Exception e) {
                                        //This is what happens when jgit fails :(
                                        //TODO Handle the diffent cases of exceptions
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
                authenticateAndRun("cloneOperation");
            } catch (Exception e) {
                //This is what happens when jgit fails :(
                //TODO Handle the diffent cases of exceptions
                e.printStackTrace();
            }
        }
    }

    public void cloneOperation(UsernamePasswordCredentialsProvider provider) {

        // remember the settings
        SharedPreferences.Editor editor = settings.edit();

        // TODO this is not pretty, use the information obtained earlier
        editor.putString("git_remote_server", hostname.split("@")[1].split(":")[0]);
        editor.putString("git_remote_location", hostname.split("@")[1].split(":")[1]);
        editor.putString("git_remote_username", hostname.split("@")[0]);
        editor.putString("git_remote_protocol", protocol);
        editor.putString("git_remote_auth", connectionMode);
        editor.putString("git_remote_port", port);
        editor.commit();

        CloneCommand cmd = Git.cloneRepository().
                setCredentialsProvider(provider).
                setCloneAllBranches(true).
                setDirectory(localDir).
                setURI(hostname);

        new GitAsyncTask(activity, true, false, CloneCommand.class).execute(cmd);
    }

    public void pullOperation(UsernamePasswordCredentialsProvider provider) {

        if (settings.getString("git_remote_username", "").isEmpty() ||
            settings.getString("git_remote_server", "").isEmpty() ||
            settings.getString("git_remote_location", "").isEmpty() )
            new AlertDialog.Builder(this)
                    .setMessage("You have to set the information about the server before synchronizing with the server")
                    .setPositiveButton("On my way!", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(activity, UserPreference.class);
                            startActivityForResult(intent, REQUEST_PULL);
                        }
                    })
                    .setNegativeButton("Nah... later", new DialogInterface.OnClickListener() {
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
            PasswordRepository.addRemote("origin", settings.getString("git_remote_username", "user")
                    + "@" +
                    settings.getString("git_remote_server", "server.com").trim()
                    + ":" +
                    settings.getString("git_remote_location", "path/to/repository"));

            GitCommand cmd;
            if (provider != null)
                cmd = new Git(PasswordRepository.getRepository(new File("")))
                        .pull()
                        .setRebase(true)
                        .setRemote("origin")
                        .setCredentialsProvider(provider);
            else
                cmd = new Git(PasswordRepository.getRepository(new File("")))
                        .pull()
                        .setRebase(true)
                        .setRemote("origin");

            new GitAsyncTask(activity, true, false, PullCommand.class).execute(cmd);
        }
    }


    public void pushOperation(UsernamePasswordCredentialsProvider provider) {
        if (settings.getString("git_remote_username", "user").isEmpty() ||
                settings.getString("git_remote_server", "server.com").trim().isEmpty() ||
                settings.getString("git_remote_location", "path/to/repository").isEmpty() )
            new AlertDialog.Builder(this)
                    .setMessage("You have to set the information about the server before synchronizing with the server")
                    .setPositiveButton("On my way!", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(activity, UserPreference.class);
                            startActivityForResult(intent, REQUEST_PUSH);
                        }
                    })
                    .setNegativeButton("Nah... later", new DialogInterface.OnClickListener() {
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
            PasswordRepository.addRemote("origin", settings.getString("git_remote_username", "user")
                    + "@" +
                    settings.getString("git_remote_server", "server.com").trim()
                    + ":" +
                    settings.getString("git_remote_location", "path/to/repository"));

            GitCommand cmd;
            if (provider != null)
                cmd = new Git(PasswordRepository.getRepository(new File("")))
                        .push()
                        .setPushAll()
                        .setRemote("origin")
                        .setCredentialsProvider(provider);
            else
                cmd = new Git(PasswordRepository.getRepository(new File("")))
                        .push()
                        .setPushAll()
                        .setRemote("origin");


            new GitAsyncTask(activity, true, false, PushCommand.class).execute(cmd);
        }
    }

    /** Finds the method and provides it with authentication paramters via invokeWithAuthentication */
    private void authenticateAndRun(String operation) {
        try {
            invokeWithAuthentication(this, this.getClass().getMethod(operation, UsernamePasswordCredentialsProvider.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Calls a method encapsulating a GitCommand and providing it with authentication parameters
     *
     * @param activity
     * @param method
     */
    private void invokeWithAuthentication(final GitHandler activity, final Method method) {

        if (connectionMode.equalsIgnoreCase("ssh-key")) {
            final File sshKey = new File(getFilesDir() + "/.ssh_key");
            if (!sshKey.exists()) {
                new AlertDialog.Builder(this)
                        .setMessage("Please import your SSH key file in the preferences")
                        .setTitle("No SSH key")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                try {
                                    Intent intent = new Intent(getApplicationContext(), UserPreference.class);
                                    startActivityForResult(intent, GET_SSH_KEY_FROM_CLONE);
                                } catch (Exception e) {
                                    System.out.println("Exception caught :(");
                                    e.printStackTrace();
                                }
                            }
                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // Do nothing...
                    }
                }).show();
            } else {
                final EditText passphrase = new EditText(activity);
                passphrase.setHint("Passphrase");
                passphrase.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
                passphrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

                new AlertDialog.Builder(activity)
                        .setTitle("Authenticate")
                        .setMessage("Please provide the passphrase for your SSH key. Leave it empty if there is no passphrase.")
                        .setView(passphrase)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                SshSessionFactory.setInstance(new GitConfigSessionFactory());
                                try {

                                    JschConfigSessionFactory sessionFactory = new SshConfigSessionFactory(sshKey.getAbsolutePath(), passphrase.getText().toString());
                                    SshSessionFactory.setInstance(sessionFactory);

                                    try {
                                        method.invoke(activity, (UsernamePasswordCredentialsProvider) null);
                                    } catch (Exception e){
                                        e.printStackTrace();
                                    }

                                } catch (Exception e){
                                    e.printStackTrace();
                                }

                            }
                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Do nothing.
                    }
                }).show();
            }
        } else {
            if (protocol.equals("ssh://")) {
                final EditText password = new EditText(activity);
                password.setHint("Password");
                password.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
                password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

                new AlertDialog.Builder(activity)
                        .setTitle("Authenticate")
                        .setMessage("Please provide the password for this repository")
                        .setView(password)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                SshSessionFactory.setInstance(new GitConfigSessionFactory());
                                try {
                                    method.invoke(activity,
                                            new UsernamePasswordCredentialsProvider(
                                                    settings.getString("git_remote_username", "git"),
                                                    password.getText().toString())
                                    );
                                } catch (Exception e){
                                    e.printStackTrace();
                                }

                            }
                        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Do nothing.
                    }
                }).show();
            } else {
                // BUG: we do not support HTTP yet...
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

            switch (requestCode) {
                case REQUEST_PULL:
                    authenticateAndRun("pullOperation");
                    break;
                case REQUEST_PUSH:
                    authenticateAndRun("pushOperation");
                    break;
                case GET_SSH_KEY_FROM_CLONE:
                    authenticateAndRun("cloneOperation");
            }

        }
    }

}
