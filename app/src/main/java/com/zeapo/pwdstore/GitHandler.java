package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

// TODO move the messages to strings.xml

public class GitHandler extends Activity {

    private Activity activity;
    private Context context;

    private String protocol;
    private String connectionMode;

    private File localDir;
    private String hostname;
    private String username;

    private SharedPreferences settings;

    public static final int REQUEST_PULL = 101;
    public static final int REQUEST_PUSH = 102;
    public static final int REQUEST_CLONE = 103;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = getApplicationContext();
        activity = this;

        settings = PreferenceManager.getDefaultSharedPreferences(this.context);

        protocol = settings.getString("git_remote_protocol", "ssh://");
        connectionMode = settings.getString("git_remote_auth", "username/password");

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

                        if (selection.equalsIgnoreCase("ssh-key (not yet implemented)")) {
                            new AlertDialog.Builder(activity)
                                    .setMessage("Authentication method not implemented yet")
                                    .setPositiveButton("OK",
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    dialog.cancel();
                                                }
                                            }
                                    ).show();
                            ((Button) findViewById(R.id.clone_button)).setEnabled(false);
                        } else {
                            ((Button) findViewById(R.id.clone_button)).setEnabled(true);
                        }
                        connectionMode = selection;
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {

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

    @Override
    public void onResume() {
        super.onResume();

        if (findViewById(R.id.clone_uri) != null) {
            ((EditText) findViewById(R.id.clone_uri)).setText(
                    settings.getString("git_remote_username", "user")
                            + "@" +
                            settings.getString("git_remote_server", "server.com")
                            + ":" +
                            settings.getString("git_remote_location", "path/to/repository")
            );

        }
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
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /* The clone process has to be on a different thread than the main one */
    private class CloneTask extends AsyncTask<CloneCommand, Integer, Integer> {
        private ProgressDialog dialog;

        public CloneTask(Activity activity) {
            context = activity;
            dialog = new ProgressDialog(context);
        }

        protected void onPreExecute() {
            this.dialog.setMessage("Cloning...");
            this.dialog.setCancelable(false);
            this.dialog.show();
        }

        protected void onPostExecute(Integer result) {
            switch (result) {
                case -1:
                    new AlertDialog.Builder(activity).
                            setTitle("Please check that the repository path is correct.").
                            setMessage("Did you forget to specify the path after the hostname?").
                            setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {

                                }
                            }).show();
                    break;
                case -2:
                    new AlertDialog.Builder(activity).
                            setTitle("Communication error").
                            setMessage("JGit said that the server didn't like our request. Either an authentication issue or the host is not reachable. Check the debug messages.").
                            setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {

                                }
                            }).show();
                    break;
                case -99:
                    new AlertDialog.Builder(activity).
                            setTitle("JGit raised an internal exception").
                            setMessage("OUPS, JGit didn't like what you did... Check that you provided it with a correct URI. Check also debug messages.").
                            setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {

                                }
                            }).show();
                    break;
                default:
                    this.dialog.dismiss();
                    setResult(RESULT_OK);
                    finish();
                    return;
            }
            this.dialog.dismiss();

            // if we were unable to finish the job
            try {
                FileUtils.deleteDirectory(localDir);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        protected Integer doInBackground(CloneCommand... cmd) {
            int count = cmd.length;
            Integer totalSize = 0;
            for (int i = 0; i < count; i++) {
                try {
                    cmd[i].call();
                } catch (JGitInternalException e) {
                    e.printStackTrace();
                    return -99;
                } catch (InvalidRemoteException e) {
                    e.printStackTrace();
                    return -1;
                } catch (TransportException e) {
                    e.printStackTrace();
                    return -2;
                } catch (Exception e) {
                    e.printStackTrace();
                    return -99;
                }
                totalSize++;
            }
            return totalSize;
        }
    }

    protected class GitConfigSessionFactory extends JschConfigSessionFactory {

        public void configure(OpenSshConfig.Host hc, Session session) {
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


    public void cloneRepository(View view) {
        localDir = new File(getApplicationContext().getFilesDir().getAbsoluteFile() + "/store");

        hostname = ((TextView) findViewById(R.id.clone_uri)).getText().toString();
        // don't ask the user, take off the protocol that he puts in
        hostname = hostname.replaceFirst("^.+://", "");
        ((TextView) findViewById(R.id.clone_uri)).setText(hostname);

        // now cheat a little and prepend the real protocol
        // jGit does not accept a ssh:// but requires https://
        if (!protocol.equals("ssh://")) {
            hostname = protocol + hostname;
        } else {
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

        editor.putString("git_remote_server", hostname.split("@")[1].split(":")[0]);
        editor.putString("git_remote_location", hostname.split("@")[1].split(":")[1]);
        editor.putString("git_remote_username", hostname.split("@")[0]);
        editor.putString("git_remote_protocol", protocol);
        editor.putString("git_remote_auth", connectionMode);
        editor.commit();

        CloneCommand cmd = Git.cloneRepository().
                setCredentialsProvider(provider).
                setCloneAllBranches(true).
                setDirectory(localDir).
                setURI(hostname);

        new CloneTask(activity).execute(cmd);
    }

    public void pullOperation(UsernamePasswordCredentialsProvider provider) {
        new GitAsyncTask(activity, true).execute(new Git(PasswordRepository.getRepository(new File("")))
            .pull()
            .setRebase(true)
            .setCredentialsProvider(provider));
    }


    public void pushOperation(UsernamePasswordCredentialsProvider provider) {
        new GitAsyncTask(activity, true).execute(new Git(PasswordRepository.getRepository(new File("")))
                .push()
                .setCredentialsProvider(provider));
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
                CloneCommand cmd = Git.cloneRepository()
                        .setDirectory(localDir)
                        .setURI(hostname)
                        .setBare(false)
                        .setNoCheckout(false)
                        .setCloneAllBranches(true);

                new CloneTask(activity).execute(cmd);
            }
        }
    }

}
