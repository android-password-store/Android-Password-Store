package com.zeapo.pwdstore.git;

import android.app.Activity;

import org.eclipse.jgit.api.Git;

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
     * Sets the command using the repository uri
     * @param uri the uri of the repository
     * @return the current object
     */
    public PushOperation setCommand(String uri) {
        this.command = new Git(repository)
                .push()
                .setPushAll()
                .setRemote("origin")
                .setCredentialsProvider(provider);
        return this;
    }
}
