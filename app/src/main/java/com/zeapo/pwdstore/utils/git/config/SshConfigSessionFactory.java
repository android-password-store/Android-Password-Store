package com.zeapo.pwdstore.utils.git.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProviderUserInfo;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;

public class SshConfigSessionFactory extends GitConfigSessionFactory {
    private String sshKey;
    private String passphrase;
    private String username;

    public SshConfigSessionFactory(String sshKey, String username, String passphrase) {
        this.sshKey = sshKey;
        this.passphrase = passphrase;
        this.username = username;
    }

    @Override
    protected JSch
    getJSch(final OpenSshConfig.Host hc, FS fs) throws JSchException {
        JSch jsch = super.getJSch(hc, fs);
        jsch.removeAllIdentity();
        jsch.addIdentity(sshKey);
        return jsch;
    }

    @Override
    protected void configure(OpenSshConfig.Host hc, Session session) {
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "publickey,password");

        CredentialsProvider provider = new CredentialsProvider() {
            @Override
            public boolean isInteractive() {
                return false;
            }

            @Override
            public boolean supports(CredentialItem... items) {
                return true;
            }

            @Override
            public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
                for (CredentialItem item : items) {
                    if (item instanceof CredentialItem.Username) {
                        ((CredentialItem.Username) item).setValue(username);
                        continue;
                    }
                    if (item instanceof CredentialItem.StringType) {
                        ((CredentialItem.StringType) item).setValue(passphrase);
                    }
                }
                return true;
            }
        };
        UserInfo userInfo = new CredentialsProviderUserInfo(session, provider);
        session.setUserInfo(userInfo);
    }
}
