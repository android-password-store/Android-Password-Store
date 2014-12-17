package com.zeapo.pwdstore.git;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.UserPreference;
import com.zeapo.pwdstore.git.config.GitConfigSessionFactory;
import com.zeapo.pwdstore.git.config.SshConfigSessionFactory;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;

public abstract class GitOperation {
    private static final String TAG = "GitOpt";
    public static final int GET_SSH_KEY_FROM_CLONE = 201;

    protected final Repository repository;
    protected final Activity callingActivity;
    protected UsernamePasswordCredentialsProvider provider;
    protected GitCommand command;

    /**
     * Creates a new git operation
     * @param fileDir the git working tree directory
     * @param callingActivity the calling activity
     */
    public GitOperation(File fileDir, Activity callingActivity) {
        this.repository = PasswordRepository.getRepository(fileDir);
        this.callingActivity = callingActivity;
    }

    /**
     * Sets the authentication using user/pwd scheme
     * @param username the username
     * @param password the password
     * @return the current object
     */
    public GitOperation setAuthentication(String username, String password) {
        SshSessionFactory.setInstance(new GitConfigSessionFactory());
        this.provider = new UsernamePasswordCredentialsProvider(username,password);
        return this;
    }

    /**
     * Sets the authentication using ssh-key scheme
     * @param sshKey the ssh-key file
     * @param username the username
     * @param passphrase the passphrase
     * @return the current object
     */
    public GitOperation setAuthentication(File sshKey, String username, String passphrase) {
        JschConfigSessionFactory sessionFactory = new SshConfigSessionFactory(sshKey.getAbsolutePath(), username, passphrase);
        SshSessionFactory.setInstance(sessionFactory);
        this.provider = null;
        return this;
    }

    /**
     * Executes the GitCommand in an async task
     * @throws Exception
     */
    public void execute() throws Exception {
        Log.d(TAG, command + " << ");
        new GitAsyncTask(callingActivity, true, false, CloneCommand.class).execute(command);
    }

    public void executeAfterAuthentication(String connectionMode, final String username, @Nullable final File sshKey) throws Exception {
        if (connectionMode.equalsIgnoreCase("ssh-key")) {
            if (sshKey == null || !sshKey.exists()) {
                new AlertDialog.Builder(callingActivity)
                        .setMessage(callingActivity.getResources().getString(R.string.ssh_preferences_dialog_text))
                        .setTitle(callingActivity.getResources().getString(R.string.ssh_preferences_dialog_title))
                        .setPositiveButton(callingActivity.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                try {
                                    Intent intent = new Intent(callingActivity.getApplicationContext(), UserPreference.class);
                                    intent.putExtra("operation", "get_ssh_key");
                                    callingActivity.startActivityForResult(intent, GET_SSH_KEY_FROM_CLONE);
                                } catch (Exception e) {
                                    System.out.println("Exception caught :(");
                                    e.printStackTrace();
                                }
                            }
                        }).setNegativeButton(callingActivity.getResources().getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // Do nothing...
                    }
                }).show();
            } else {
                final EditText passphrase = new EditText(callingActivity);
                passphrase.setHint("Passphrase");
                passphrase.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
                passphrase.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

                new AlertDialog.Builder(callingActivity)
                        .setTitle(callingActivity.getResources().getString(R.string.passphrase_dialog_title))
                        .setMessage(callingActivity.getResources().getString(R.string.passphrase_dialog_text))
                        .setView(passphrase)
                        .setPositiveButton(callingActivity.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                SshSessionFactory.setInstance(new GitConfigSessionFactory());
                                try {
                                    setAuthentication(sshKey, username, passphrase.getText().toString()).execute();
                                } catch (Exception e){
                                    e.printStackTrace();
                                }

                            }
                        }).setNegativeButton(callingActivity.getResources().getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // Do nothing.
                    }
                }).show();
            }
        } else {
            final EditText password = new EditText(callingActivity);
            password.setHint("Password");
            password.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
            password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            new AlertDialog.Builder(callingActivity)
                    .setTitle(callingActivity.getResources().getString(R.string.passphrase_dialog_title))
                    .setMessage(callingActivity.getResources().getString(R.string.password_dialog_text))
                    .setView(password)
                    .setPositiveButton(callingActivity.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                            setAuthentication(username, password.getText().toString());
                            try {
                                execute();
                            } catch (Exception e){
                                e.printStackTrace();
                            }

                        }
                    }).setNegativeButton(callingActivity.getResources().getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Do nothing.
                }
            }).show();
        }
    }
}
