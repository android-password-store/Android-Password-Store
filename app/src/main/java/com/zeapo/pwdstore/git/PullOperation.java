package com.zeapo.pwdstore.git;

import android.app.Activity;

import com.zeapo.pwdstore.utils.PasswordRepository;

import org.eclipse.jgit.api.Git;

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
                .setRemote("origin")
                .setCredentialsProvider(provider);
        return this;
    }
}
