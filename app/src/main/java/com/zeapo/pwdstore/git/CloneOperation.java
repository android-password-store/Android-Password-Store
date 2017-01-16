package com.zeapo.pwdstore.git;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.zeapo.pwdstore.R;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;

import java.io.File;

public class CloneOperation extends GitOperation {
    private static final String TAG = "CLONEOPT";

    /**
     * Creates a new clone operation
     *
     * @param fileDir         the git working tree directory
     * @param callingActivity the calling activity
     */
    public CloneOperation(File fileDir, Activity callingActivity) {
        super(fileDir, callingActivity);
    }

    /**
     * Sets the command using the repository uri
     *
     * @param uri the uri of the repository
     * @return the current object
     */
    public CloneOperation setCommand(String uri) {
        this.command = Git.cloneRepository().
                setCloneAllBranches(true).
                setDirectory(repository.getWorkTree()).
                setURI(uri);
        return this;
    }

    /**
     * sets the authentication for user/pwd scheme
     *
     * @param username the username
     * @param password the password
     * @return the current object
     */
    @Override
    public CloneOperation setAuthentication(String username, String password) {
        super.setAuthentication(username, password);
        return this;
    }

    /**
     * sets the authentication for the ssh-key scheme
     *
     * @param sshKey     the ssh-key file
     * @param username   the username
     * @param passphrase the passphrase
     * @return the current object
     */
    @Override
    public CloneOperation setAuthentication(File sshKey, String username, String passphrase) {
        super.setAuthentication(sshKey, username, passphrase);
        return this;
    }

    @Override
    public void execute() {
        if (this.provider != null) {
            ((CloneCommand) this.command).setCredentialsProvider(this.provider);
        }
        new GitAsyncTask(callingActivity, true, false, this).execute(this.command);
    }

    @Override
    public void onTaskEnded(String result) {
        new AlertDialog.Builder(callingActivity).
                setTitle(callingActivity.getResources().getString(R.string.jgit_error_dialog_title)).
                setMessage("Error occured during the clone operation, "
                        + callingActivity.getResources().getString(R.string.jgit_error_dialog_text)
                        + result
                        + "\nPlease check the FAQ for possible reasons why this error might occur.").
                setPositiveButton(callingActivity.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                    }
                }).show();
    }
}
