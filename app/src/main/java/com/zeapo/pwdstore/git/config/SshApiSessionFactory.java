/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jcraft.jsch.Identity;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.git.BaseGitActivity;
import com.zeapo.pwdstore.utils.PreferenceKeys;

import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.CredentialsProviderUserInfo;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.Base64;
import org.eclipse.jgit.util.FS;
import org.openintents.ssh.authentication.ISshAuthenticationService;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.openintents.ssh.authentication.SshAuthenticationApiError;
import org.openintents.ssh.authentication.SshAuthenticationConnection;
import org.openintents.ssh.authentication.request.KeySelectionRequest;
import org.openintents.ssh.authentication.request.Request;
import org.openintents.ssh.authentication.request.SigningRequest;
import org.openintents.ssh.authentication.request.SshPublicKeyRequest;
import org.openintents.ssh.authentication.util.SshAuthenticationApiUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class SshApiSessionFactory extends JschConfigSessionFactory {
    /**
     * Intent request code indicating a completed signature that should be posted to an outstanding
     * ApiIdentity
     */
    public static final int POST_SIGNATURE = 301;

    private String username;
    private Identity identity;

    public SshApiSessionFactory(String username, Identity identity) {
        this.username = username;
        this.identity = identity;
    }

    @NonNull
    @Override
    protected JSch getJSch(@NonNull final OpenSshConfig.Host hc, @NonNull FS fs)
        throws JSchException {
        JSch jsch = super.getJSch(hc, fs);
        jsch.removeAllIdentity();
        jsch.addIdentity(identity, null);
        return jsch;
    }

    @Override
    protected void configure(@NonNull OpenSshConfig.Host hc, Session session) {
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "publickey");

        CredentialsProvider provider =
            new CredentialsProvider() {
                @Override
                public boolean isInteractive() {
                    return false;
                }

                @Override
                public boolean supports(CredentialItem... items) {
                    return true;
                }

                @Override
                public boolean get(URIish uri, CredentialItem... items)
                    throws UnsupportedCredentialItem {
                    for (CredentialItem item : items) {
                        if (item instanceof CredentialItem.Username) {
                            ((CredentialItem.Username) item).setValue(username);
                        }
                    }
                    return true;
                }
            };
        UserInfo userInfo = new CredentialsProviderUserInfo(session, provider);
        session.setUserInfo(userInfo);
    }

    /**
     * Helper to build up an ApiIdentity via the invocation of several pending intents that
     * communicate with OpenKeychain. The user of this class must handle onActivityResult and keep
     * feeding the resulting intents into the IdentityBuilder until it can successfully complete the
     * build.
     */
    public static class IdentityBuilder {
        private SshAuthenticationConnection connection;
        private SshAuthenticationApi api;
        private String keyId, description, alg;
        private byte[] publicKey;
        private BaseGitActivity callingActivity;
        private SharedPreferences settings;

        /**
         * Construct a new IdentityBuilder
         *
         * @param callingActivity Activity that will be used to launch pending intents and that will
         *                        receive and handle the results.
         */
        public IdentityBuilder(BaseGitActivity callingActivity) {
            this.callingActivity = callingActivity;

            List<String> providers =
                SshAuthenticationApiUtils.getAuthenticationProviderPackageNames(
                    callingActivity);
            if (providers.isEmpty())
                throw new RuntimeException(callingActivity.getString(R.string.no_ssh_api_provider));

            // TODO: Handle multiple available providers? Are there actually any in practice beyond
            // OpenKeychain?
            connection = new SshAuthenticationConnection(callingActivity, providers.get(0));

            settings =
                PreferenceManager.getDefaultSharedPreferences(
                    callingActivity.getApplicationContext());
            keyId = settings.getString(PreferenceKeys.SSH_OPENKEYSTORE_KEYID, null);
        }

        /**
         * Free any resources associated with this IdentityBuilder
         */
        public void close() {
            if (connection != null && connection.isConnected()) connection.disconnect();
        }

        /**
         * Helper to invoke an OpenKeyshain SSH API method and correctly interpret the result.
         *
         * @param request     The request intent to launch
         * @param requestCode The request code to use if a pending intent needs to be sent
         * @return The resulting intent if the request completed immediately, or null if we had to
         * launch a pending intent to interact with the user
         */
        private Intent executeApi(Request request, int requestCode) {
            Intent result = api.executeApi(request.toIntent());

            switch (result.getIntExtra(SshAuthenticationApi.EXTRA_RESULT_CODE, -1)) {
                case SshAuthenticationApi.RESULT_CODE_ERROR:
                    SshAuthenticationApiError error =
                        result.getParcelableExtra(SshAuthenticationApi.EXTRA_ERROR);
                    // On an OpenKeychain SSH API error, clear out the stored keyid
                    settings.edit().putString(PreferenceKeys.SSH_OPENKEYSTORE_KEYID, null).apply();

                    switch (error.getError()) {
                        // If the problem was just a bad keyid, reset to allow them to choose a
                        // different one
                        case (SshAuthenticationApiError.NO_SUCH_KEY):
                        case (SshAuthenticationApiError.NO_AUTH_KEY):
                            keyId = null;
                            publicKey = null;
                            description = null;
                            alg = null;
                            return executeApi(new KeySelectionRequest(), requestCode);

                        // Other errors are fatal
                        default:
                            throw new RuntimeException(error.getMessage());
                    }
                case SshAuthenticationApi.RESULT_CODE_SUCCESS:
                    break;
                case SshAuthenticationApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                    PendingIntent pendingIntent =
                        result.getParcelableExtra(SshAuthenticationApi.EXTRA_PENDING_INTENT);
                    try {
                        callingActivity.startIntentSenderForResult(
                            pendingIntent.getIntentSender(), requestCode, null, 0, 0, 0);
                        return null;
                    } catch (IntentSender.SendIntentException e) {
                        e.printStackTrace();
                        throw new RuntimeException(
                            callingActivity.getString(R.string.ssh_api_pending_intent_failed));
                    }
                default:
                    throw new RuntimeException(
                        callingActivity.getString(R.string.ssh_api_unknown_error));
            }

            return result;
        }

        /**
         * Parse a given intent to see if it is the result of an OpenKeychain pending intent. If so,
         * extract any updated state from it.
         *
         * @param intent The intent to inspect
         */
        public void consume(Intent intent) {
            if (intent == null) return;

            if (intent.hasExtra(SshAuthenticationApi.EXTRA_KEY_ID)) {
                keyId = intent.getStringExtra(SshAuthenticationApi.EXTRA_KEY_ID);
                description = intent.getStringExtra(SshAuthenticationApi.EXTRA_KEY_DESCRIPTION);
                settings.edit().putString(PreferenceKeys.SSH_OPENKEYSTORE_KEYID, keyId).apply();
            }

            if (intent.hasExtra(SshAuthenticationApi.EXTRA_SSH_PUBLIC_KEY)) {
                String keyStr = intent.getStringExtra(SshAuthenticationApi.EXTRA_SSH_PUBLIC_KEY);
                String[] keyParts = keyStr.split(" ");
                alg = keyParts[0];
                publicKey = Base64.decode(keyParts[1]);
            }
        }

        /**
         * Try to build an ApiIdentity that will perform SSH authentication via OpenKeychain.
         *
         * @param requestCode The request code to use if a pending intent needs to be sent
         * @return The built identity, or null of user interaction is still required (in which case
         * a pending intent will have already been launched)
         */
        public ApiIdentity tryBuild(int requestCode) {
            // First gate, need to initiate a connection to the service and wait for it to connect.
            if (api == null) {
                connection.connect(
                    new SshAuthenticationConnection.OnBound() {
                        @Override
                        public void onBound(ISshAuthenticationService sshAgent) {
                            api = new SshAuthenticationApi(callingActivity, sshAgent);
                            // We can immediately try the next phase without needing to post
                            // back
                            // though onActivityResult
                            callingActivity.onActivityResult(
                                requestCode, Activity.RESULT_OK, null);
                        }

                        @Override
                        public void onError() {
                            new MaterialAlertDialogBuilder(callingActivity)
                                .setMessage(
                                    callingActivity.getString(
                                        R.string.openkeychain_ssh_api_connect_fail))
                                .show();
                        }
                    });

                return null;
            }

            // Second gate, need the user to select which key they want to use
            if (keyId == null) {
                consume(executeApi(new KeySelectionRequest(), requestCode));
                // If we did not immediately get the result, bail for now and wait to be re-entered
                if (keyId == null) return null;
            }

            // Third gate, need to get the public key for the selected key. This one often does not
            // need use interaction.
            if (publicKey == null) {
                consume(executeApi(new SshPublicKeyRequest(keyId), requestCode));
                // If we did not immediately get the result, bail for now and wait to be re-entered
                if (publicKey == null) return null;
            }

            // Have everything we need for now, build the identify
            return new ApiIdentity(keyId, description, publicKey, alg, callingActivity, api);
        }
    }

    /**
     * A Jsch identity that delegates key operations via the OpenKeychain SSH API
     */
    public static class ApiIdentity implements Identity {
        private String keyId, description, alg;
        private byte[] publicKey;
        private Activity callingActivity;
        private SshAuthenticationApi api;
        private CountDownLatch latch;
        private byte[] signature;

        ApiIdentity(
            String keyId,
            String description,
            byte[] publicKey,
            String alg,
            Activity callingActivity,
            SshAuthenticationApi api) {
            this.keyId = keyId;
            this.description = description;
            this.publicKey = publicKey;
            this.alg = alg;
            this.callingActivity = callingActivity;
            this.api = api;
        }

        @Override
        public boolean setPassphrase(byte[] passphrase) throws JSchException {
            // We are not encrypted with a passphrase
            return true;
        }

        @Override
        public byte[] getPublicKeyBlob() {
            return publicKey;
        }

        /**
         * Helper to handle the result of an OpenKeyshain SSH API signing request
         *
         * @param result The result intent to handle
         * @return The signed challenge, or null if it was not immediately available, in which case
         * the latch has been initialized and the pending intent started
         */
        private byte[] handleSignResult(Intent result) {
            switch (result.getIntExtra(SshAuthenticationApi.EXTRA_RESULT_CODE, -1)) {
                case SshAuthenticationApi.RESULT_CODE_ERROR:
                    SshAuthenticationApiError error =
                        result.getParcelableExtra(SshAuthenticationApi.EXTRA_ERROR);
                    throw new RuntimeException(error.getMessage());
                case SshAuthenticationApi.RESULT_CODE_SUCCESS:
                    return result.getByteArrayExtra(SshAuthenticationApi.EXTRA_SIGNATURE);
                case SshAuthenticationApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                    PendingIntent pendingIntent =
                        result.getParcelableExtra(SshAuthenticationApi.EXTRA_PENDING_INTENT);
                    try {
                        latch = new CountDownLatch(1);
                        callingActivity.startIntentSenderForResult(
                            pendingIntent.getIntentSender(), POST_SIGNATURE, null, 0, 0, 0);
                        return null;

                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(
                            callingActivity.getString(R.string.ssh_api_pending_intent_failed));
                    }
                default:
                    if (result.hasExtra(SshAuthenticationApi.EXTRA_CHALLENGE))
                        return handleSignResult(api.executeApi(result));
                    throw new RuntimeException(
                        callingActivity.getString(R.string.ssh_api_unknown_error));
            }
        }

        @Override
        public byte[] getSignature(byte[] data) {
            Intent request = new SigningRequest(data, keyId, SshAuthenticationApi.SHA1).toIntent();
            signature = handleSignResult(api.executeApi(request));

            // If we did not immediately get a signature (probable), we will block on a latch until
            // the main activity gets the intent result and posts to us.
            if (signature == null) {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            return signature;
        }

        /**
         * Post a signature response back to an in-progress operation using this ApiIdentity.
         *
         * @param data The signature data (hopefully)
         */
        public void postSignature(Intent data) {
            try {
                if (data != null) {
                    signature = handleSignResult(data);
                }
            } finally {
                if (latch != null) latch.countDown();
            }
        }

        @Override
        public boolean decrypt() {
            return true;
        }

        @Override
        public String getAlgName() {
            return alg;
        }

        @Override
        public String getName() {
            return description;
        }

        @Override
        public boolean isEncrypted() {
            return false;
        }

        @Override
        public void clear() {
        }
    }
}
