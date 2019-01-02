package com.zeapo.pwdstore.git;

import android.app.Activity;
import android.app.AlertDialog;
import com.zeapo.pwdstore.R;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;

import java.io.File;

public class PushOperation extends GitOperation {

    /**
     * Creates a new git operation
     *
     * @param fileDir         the git working tree directory
     * @param callingActivity the calling activity
     */
    public PushOperation(File fileDir, Activity callingActivity) {
        super(fileDir, callingActivity);
    }

    /**
     * Sets the command
     *
     * @return the current object
     */
    public PushOperation setCommand() {
        this.command = new Git(repository)
                .push()
                .setPushAll()
                .setRemote("origin");
        return this;
    }

    @Override
    public void execute() {
        if (this.provider != null) {
            ((PushCommand) this.command).setCredentialsProvider(this.provider);
        }
        new GitAsyncTask(callingActivity, true, false, this).execute(this.command);
    }

    @Override
    public void onError(String errorMessage) {
        // TODO handle the "Nothing to push" case
        new AlertDialog.Builder(callingActivity).
                setTitle(callingActivity.getResources().getString(R.string.jgit_error_dialog_title)).
                setMessage(callingActivity.getString(R.string.jgit_error_push_dialog_text) + errorMessage).
                setPositiveButton(callingActivity.getResources().getString(R.string.dialog_ok), (dialogInterface, i) -> callingActivity.finish()).show();
    }
}
