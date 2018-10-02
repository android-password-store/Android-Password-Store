package com.zeapo.pwdstore.git;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.UserPreference;
import com.zeapo.pwdstore.git.config.GitConfigSessionFactory;
import com.zeapo.pwdstore.git.config.SshConfigSessionFactory;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.eclipse.jgit.api.GitCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;

public abstract class GitOperation {
    public static final int GET_SSH_KEY_FROM_CLONE = 201;

    protected final Repository repository;
    final Activity callingActivity;
    UsernamePasswordCredentialsProvider provider;
    GitCommand command;

    /**
     * Creates a new git operation
     *
     * @param fileDir         the git working tree directory
     * @param callingActivity the calling activity
     */
    public GitOperation(File fileDir, Activity callingActivity) {
        this.repository = PasswordRepository.getRepository(fileDir);
        this.callingActivity = callingActivity;
    }

    /**
     * Sets the authentication using user/pwd scheme
     *
     * @param username the username
     * @param password the password
     * @return the current object
     */
    GitOperation setAuthentication(String username, String password) {
        SshSessionFactory.setInstance(new GitConfigSessionFactory());
        this.provider = new UsernamePasswordCredentialsProvider(username, password);
        return this;
    }

    /**
     * Sets the authentication using ssh-key scheme
     *
     * @param sshKey     the ssh-key file
     * @param username   the username
     * @param passphrase the passphrase
     * @return the current object
     */
    GitOperation setAuthentication(File sshKey, String username, String passphrase) {
        JschConfigSessionFactory sessionFactory = new SshConfigSessionFactory(sshKey.getAbsolutePath(), username, passphrase);
        SshSessionFactory.setInstance(sessionFactory);
        this.provider = null;
        return this;
    }

    /**
     * Executes the GitCommand in an async task
     */
    public abstract void execute();

    /**
     * Executes the GitCommand in an async task after creating the authentication
     *
     * @param connectionMode the server-connection mode
     * @param username       the username
     * @param sshKey         the ssh-key file
     */
    public void executeAfterAuthentication(final String connectionMode, final String username, @Nullable final File sshKey) {
        executeAfterAuthentication(connectionMode, username, sshKey, false);
    }

    /**
     * Executes the GitCommand in an async task after creating the authentication
     *
     * @param connectionMode the server-connection mode
     * @param username       the username
     * @param sshKey         the ssh-key file
     * @param showError      show the passphrase edit text in red
     */
    private void executeAfterAuthentication(final String connectionMode, final String username, @Nullable final File sshKey, final boolean showError) {
        if (connectionMode.equalsIgnoreCase("ssh-key")) {
            if (sshKey == null || !sshKey.exists()) {
                new AlertDialog.Builder(callingActivity)
                        .setMessage(callingActivity.getResources().getString(R.string.ssh_preferences_dialog_text))
                        .setTitle(callingActivity.getResources().getString(R.string.ssh_preferences_dialog_title))
                        .setPositiveButton(callingActivity.getResources().getString(R.string.ssh_preferences_dialog_import), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                try {
                                    // Ask the UserPreference to provide us with the ssh-key
                                    // onResult has to be handled by the callingActivity
                                    Intent intent = new Intent(callingActivity.getApplicationContext(), UserPreference.class);
                                    intent.putExtra("operation", "get_ssh_key");
                                    callingActivity.startActivityForResult(intent, GET_SSH_KEY_FROM_CLONE);
                                } catch (Exception e) {
                                    System.out.println("Exception caught :(");
                                    e.printStackTrace();
                                }
                            }
                        })
                        .setNegativeButton(callingActivity.getResources().getString(R.string.ssh_preferences_dialog_generate), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    // Duplicated code
                                    Intent intent = new Intent(callingActivity.getApplicationContext(), UserPreference.class);
                                    intent.putExtra("operation", "make_ssh_key");
                                    callingActivity.startActivityForResult(intent, GET_SSH_KEY_FROM_CLONE);
                                } catch (Exception e) {
                                    System.out.println("Exception caught :(");
                                    e.printStackTrace();
                                }
                            }
                        })
                        .setNeutralButton(callingActivity.getResources().getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                // Finish the blank GitActivity so user doesn't have to press back
                                callingActivity.finish();
                            }
                        }).show();
            } else {
                LayoutInflater layoutInflater = LayoutInflater.from(callingActivity.getApplicationContext());
                @SuppressLint("InflateParams") final View dialogView = layoutInflater.inflate(R.layout.git_passphrase_layout, null);
                final EditText passphrase = (EditText) dialogView.findViewById(R.id.sshkey_passphrase);
                final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(callingActivity.getApplicationContext());
                final String sshKeyPassphrase = settings.getString("ssh_key_passphrase", null);
                if (showError) {
                    passphrase.setError("Wrong passphrase");
                }
                JSch jsch = new JSch();
                try {
                    final KeyPair keyPair = KeyPair.load(jsch, callingActivity.getFilesDir() + "/.ssh_key");

                    if (keyPair.isEncrypted()) {
                        if (sshKeyPassphrase != null && !sshKeyPassphrase.isEmpty()) {
                            if (keyPair.decrypt(sshKeyPassphrase)) {
                                // Authenticate using the ssh-key and then execute the command
                                setAuthentication(sshKey, username, sshKeyPassphrase).execute();
                            } else {
                                // call back the method
                                executeAfterAuthentication(connectionMode, username, sshKey, true);
                            }
                        } else {
                            new AlertDialog.Builder(callingActivity)
                                    .setTitle(callingActivity.getResources().getString(R.string.passphrase_dialog_title))
                                    .setMessage(callingActivity.getResources().getString(R.string.passphrase_dialog_text))
                                    .setView(dialogView)
                                    .setPositiveButton(callingActivity.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            if (keyPair.decrypt(passphrase.getText().toString())) {
                                                boolean rememberPassphrase = ((CheckBox) dialogView.findViewById(R.id.sshkey_remember_passphrase)).isChecked();
                                                if (rememberPassphrase) {
                                                    settings.edit().putString("ssh_key_passphrase", passphrase.getText().toString()).apply();
                                                }
                                                // Authenticate using the ssh-key and then execute the command
                                                setAuthentication(sshKey, username, passphrase.getText().toString()).execute();
                                            } else {
                                                settings.edit().putString("ssh_key_passphrase", null).apply();
                                                // call back the method
                                                executeAfterAuthentication(connectionMode, username, sshKey, true);
                                            }
                                        }
                                    }).setNegativeButton(callingActivity.getResources().getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    // Do nothing.
                                }
                            }).show();
                        }
                    } else {
                        setAuthentication(sshKey, username, "").execute();
                    }
                } catch (JSchException e) {
                    new AlertDialog.Builder(callingActivity)
                            .setTitle("Unable to open the ssh-key")
                            .setMessage("Please check that it was imported.")
                            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {

                                }
                            }).show();
                }
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
                            // authenticate using the user/pwd and then execute the command
                            setAuthentication(username, password.getText().toString()).execute();

                        }
                    }).setNegativeButton(callingActivity.getResources().getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Do nothing.
                }
            }).show();
        }
    }

    /**
     * Action to execute on error
     */
    public void onError(String errorMessage) {
        new AlertDialog.Builder(callingActivity).
                setTitle(callingActivity.getResources().getString(R.string.jgit_error_dialog_title)).
                setMessage(callingActivity.getResources().getString(R.string.jgit_error_dialog_text) + errorMessage).
                setPositiveButton(callingActivity.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        callingActivity.setResult(Activity.RESULT_CANCELED);
                        callingActivity.finish();
                    }
                }).show();
    }

    /**
     * Action to execute on success
     */
    public void onSuccess() {
    }
}
