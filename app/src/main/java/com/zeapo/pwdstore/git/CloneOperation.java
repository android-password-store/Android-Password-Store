package com.zeapo.pwdstore.git;

import android.app.Activity;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;

public class CloneOperation extends GitOperation {
    private static final String TAG = "CLONEOPT";

    /**
     * Creates a new clone operation
     * @param fileDir the git working tree directory
     * @param callingActivity the calling activity
     */
    public CloneOperation(File fileDir, Activity callingActivity) {
        super(fileDir, callingActivity);
    }

    /**
     * Sets the command using the repository uri
     * @param uri the uri of the repository
     * @return the current object
     */
    public CloneOperation setCommand(String uri) {
        this.command = Git.cloneRepository().
                setCredentialsProvider(provider).
                setCloneAllBranches(true).
                setDirectory(repository.getWorkTree()).
                setURI(uri);
        return this;
    }

    /**
     * sets the authentication for user/pwd scheme
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
}
