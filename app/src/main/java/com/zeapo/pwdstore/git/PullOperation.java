package com.zeapo.pwdstore.git;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.zeapo.pwdstore.R;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;

import java.io.File;

public class PullOperation extends GitOperation {

    /**
     * Creates a new git operation
     *
     * @param fileDir         the git working tree directory
     * @param callingActivity the calling activity
     */
    public PullOperation(File fileDir, Activity callingActivity) {
        super(fileDir, callingActivity);
    }

    /**
     * Sets the command
     * @return the current object
     */
    public PullOperation setCommand() {
        this.command = new Git(repository)
                .pull()
                .setRebase(true)
                .setRemote("origin");
        return this;
    }

    @Override
    public void execute() {
        if (this.provider != null) {
            ((PullCommand) this.command).setCredentialsProvider(this.provider);
        }
        new GitAsyncTask(callingActivity, true, false, this).execute(this.command);
    }

    @Override
    public void onTaskEnded(String result) {
        new AlertDialog.Builder(callingActivity).
                setTitle(callingActivity.getResources().getString(R.string.jgit_error_dialog_title)).
                setMessage("Error occured during the pull operation, "
                        + callingActivity.getResources().getString(R.string.jgit_error_dialog_text)
                        + result
                        + "\nPlease check the FAQ for possible reasons why this error might occur.").
                setPositiveButton(callingActivity.getResources().getString(R.string.dialog_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        callingActivity.finish();
                    }
                }).show();
    }
}
