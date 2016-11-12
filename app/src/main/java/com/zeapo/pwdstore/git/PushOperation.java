package com.zeapo.pwdstore.git;

import android.app.Activity;

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
}
