package com.zeapo.pwdstore.git;

import android.app.Activity;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PushCommand;

import java.io.File;

public class SyncOperation extends GitOperation {
    protected PullCommand pullCommand;
    protected PushCommand pushCommand;

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
     * @return the current object
     */
    public SyncOperation setCommands() {
        this.pullCommand = new Git(repository)
                .pull()
                .setRebase(true)
                .setRemote("origin");
        this.pushCommand = new Git(repository)
                .push()
                .setPushAll()
                .setRemote("origin");
        return this;
    }

    @Override
    public void execute() {
        if (this.provider != null) {
            this.pullCommand.setCredentialsProvider(this.provider);
            this.pushCommand.setCredentialsProvider(this.provider);
        }
        new GitAsyncTask(callingActivity, true, false, this).execute(this.pullCommand, this.pushCommand);
    }
}
