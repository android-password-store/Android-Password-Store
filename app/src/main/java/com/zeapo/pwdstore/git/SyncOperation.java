package com.zeapo.pwdstore.git;

import android.app.Activity;
import android.app.AlertDialog;
import com.zeapo.pwdstore.R;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.StatusCommand;

import java.io.File;

public class SyncOperation extends GitOperation {
    private AddCommand addCommand;
    private StatusCommand statusCommand;
    private CommitCommand commitCommand;
    private PullCommand pullCommand;
    private PushCommand pushCommand;

    /**
     * Creates a new git operation
     *
     * @param fileDir         the git working tree directory
     * @param callingActivity the calling activity
     */
    public SyncOperation(File fileDir, Activity callingActivity) {
        super(fileDir, callingActivity);
    }

    /**
     * Sets the command
     *
     * @return the current object
     */
    public SyncOperation setCommands() {
        Git git = new Git(repository);
        this.addCommand = git.add().addFilepattern(".");
        this.statusCommand = git.status();
        this.commitCommand = git.commit().setAll(true).setMessage("[Android Password Store] Sync");
        this.pullCommand = git.pull().setRebase(true).setRemote("origin");
        this.pushCommand = git.push().setPushAll().setRemote("origin");
        return this;
    }

    @Override
    public void execute() {
        if (this.provider != null) {
            this.pullCommand.setCredentialsProvider(this.provider);
            this.pushCommand.setCredentialsProvider(this.provider);
        }
        new GitAsyncTask(callingActivity, true, false, this).execute(this.addCommand, this.statusCommand, this.commitCommand, this.pullCommand, this.pushCommand);
    }

    @Override
    public void onError(String errorMessage) {
        new AlertDialog.Builder(callingActivity).
                setTitle(callingActivity.getResources().getString(R.string.jgit_error_dialog_title)).
                setMessage("Error occured during the sync operation, "
                        + callingActivity.getResources().getString(R.string.jgit_error_dialog_text)
                        + errorMessage
                        + "\nPlease check the FAQ for possible reasons why this error might occur.").
                setPositiveButton(callingActivity.getResources().getString(R.string.dialog_ok), (dialogInterface, i) -> callingActivity.finish()).show();
    }
}
