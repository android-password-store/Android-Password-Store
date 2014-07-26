package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.text.InputType;
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
import android.widget.Toast;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.zeapo.pwdstore.R;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProviderUserInfo;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class GitClone extends Activity {

    private Activity activity;
    private Context context;

    private String protocol;
    private String connectionMode;

    private File localDir;
    private String hostname;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_git_clone);

        context = getApplicationContext();
        activity = this;

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

                if (selection.equalsIgnoreCase("ssh-key")) {
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
    private class CloneTask extends AsyncTask<CloneCommand, Integer, Long> {
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

        protected void onPostExecute(Long result) {
            if (result < 0) {
                new AlertDialog.Builder(activity).
                        setTitle("Invalid remote repository path").
                        setMessage("Please check that the repository path is correct.\nDid you forget to specify the path after the hostname?").
                        setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        }).show();
            }
            this.dialog.dismiss();
        }


        protected Long doInBackground(CloneCommand... cmd) {
            int count = cmd.length;
            long totalSize = 0;
            for (int i = 0; i < count; i++) {
                try {
                    cmd[i].call();
                } catch (InvalidRemoteException e) {
                    return new Long(-1);
                } catch (Exception e) {
                    e.printStackTrace();
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
        localDir = new File(getApplicationContext().getCacheDir().getAbsoluteFile() + "/store");

        hostname = ((TextView) findViewById(R.id.clone_uri)).getText().toString();
        // don't ask the user, take off the protocol that he puts in
        hostname = hostname.replaceFirst("^.+://", "");
        ((TextView) findViewById(R.id.clone_uri)).setText(hostname);

        // now cheat a little and prepend the real protocol
        // jGit does not accept a ssh:// but requires https://
        if (!protocol.equals("ssh://"))  hostname = new String(protocol + hostname);

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
                                        authenticateThenClone(localDir);
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
                authenticateThenClone(localDir);
            } catch (Exception e) {
                //This is what happens when jgit fails :(
                //TODO Handle the diffent cases of exceptions
                e.printStackTrace();
            }
        }
    }


    private void authenticateThenClone(final File localDir) {
        String connectionMode = ((Spinner) findViewById(R.id.connection_mode)).getSelectedItem().toString();

        if (connectionMode.equalsIgnoreCase("ssh-key")) {

        } else {
            // Set an EditText view to get user input
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText username = new EditText(activity);
            username.setHint("Username");
            username.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);

            final EditText password = new EditText(activity);
            password.setHint("Password");
            password.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
            password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            layout.addView(username);
            layout.addView(password);

            new AlertDialog.Builder(activity)
                    .setTitle("Authenticate")
                    .setMessage("Please provide your usename and password for this repository")
                    .setView(layout)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                            SshSessionFactory.setInstance(new GitConfigSessionFactory());

                            CloneCommand cmd = Git.cloneRepository().
                                    setCredentialsProvider(new UsernamePasswordCredentialsProvider("git", "nicomint")).
                                    setCloneAllBranches(true).
                                    setDirectory(localDir).
                                    setURI(hostname);

                            new CloneTask(activity).execute(cmd);
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Do nothing.
                }
            }).show();
        }
    }



}
