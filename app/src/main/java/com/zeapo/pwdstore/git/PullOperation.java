package com.zeapo.pwdstore.git;

import android.app.Activity;

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
     * Sets the command using the repository uri
     * @param uri the uri of the repository
     * @return the current object
     */
    public PullOperation setCommand(String uri) {
        this.command = new Git(repository)
                .pull()
                .setRebase(true)
                .setRemote("origin");
        return this;
    }

    @Override
    public void execute() throws Exception  {
        if (this.provider != null) {
            ((PullCommand) this.command).setCredentialsProvider(this.provider);
        }
        new GitAsyncTask(callingActivity, true, false, PullCommand.class).execute(this.command);
    }
}
