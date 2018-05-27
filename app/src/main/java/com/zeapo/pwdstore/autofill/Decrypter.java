package com.zeapo.pwdstore.autofill;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.zeapo.pwdstore.PasswordEntry;

import org.apache.commons.io.FileUtils;
import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TargetApi(26)
public class Decrypter {
    Context applicationContext;

    private static final String TAG = "PasswordStoreAutofill";

    public Decrypter(
            Context applicationContext
    ) {
        this.applicationContext = applicationContext;
    }

    void bindOpenPgpService(OpenPgpServiceConnection.OnBound listener) {
        SharedPreferences settings =
                PreferenceManager.getDefaultSharedPreferences(applicationContext);
        String pgpProviderPackageName = settings.getString("openpgp_provider_list", "");
        OpenPgpServiceConnection serviceConnection;
        if (TextUtils.isEmpty(pgpProviderPackageName)) {
            Log.w(TAG, "No pgp provider selected, autofill disabled");
            return;
        }
        serviceConnection = new OpenPgpServiceConnection(
                applicationContext,
                pgpProviderPackageName,
                listener
        );
        serviceConnection.bindToService();
    }

    private PasswordEntry decrypt(File item, IOpenPgpService2 service) throws
            PgpAuthenticationRequiredException {
        Intent data = new Intent();
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        InputStream is = null;
        try {
            is = FileUtils.openInputStream(item);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();

        OpenPgpApi api = new OpenPgpApi(applicationContext, service);
        Intent result = api.executeApi(data, is, os);

        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            case OpenPgpApi.RESULT_CODE_SUCCESS: {
                try {
                    return new PasswordEntry(os);
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "UnsupportedEncodingException", e);
                }
                break;
            }
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                throw new PgpAuthenticationRequiredException(result);
            }
            case OpenPgpApi.RESULT_CODE_ERROR: {
                OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                Toast.makeText(
                        applicationContext,
                        "Error from OpenKeyChain : " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
                Log.e(TAG, "onError getErrorId:" + error.getErrorId());
                Log.e(TAG, "onError getMessage:" + error.getMessage());
                break;
            }
        }
        return null;
    }

    public void bindAndDecrypt(final File item, final DecryptionListener decryptionListener) {
        bindOpenPgpService(new OpenPgpServiceConnection.OnBound() {
            @Override
            public void onBound(IOpenPgpService2 service) {
                try {
                    PasswordEntry result = decrypt(item, service);
                    decryptionListener.onDecrypted(result);
                } catch (PgpAuthenticationRequiredException e) {
                    List<File> results = new ArrayList<>();
                    results.add(item);
                    decryptionListener.onAuthenticationRequired(results, e.result);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to bin to OpenPGP Service");
            }
        });
    }

    public void bindAndDecryptBatch(
            final List<File> batch,
            final DecryptionListener decryptionListener
    ) {
        bindOpenPgpService(new OpenPgpServiceConnection.OnBound() {
            @Override
            public void onBound(IOpenPgpService2 service) {
                Map<File, PasswordEntry> results = new HashMap<>();
                for (File file : batch) {
                    try {
                        results.put(file, decrypt(file, service));
                    } catch (PgpAuthenticationRequiredException e) {
                        decryptionListener.onAuthenticationRequired(batch, e.result);
                        return;
                    }
                }
                decryptionListener.onBatchDecrypted(results);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to bin to OpenPGP Service");
            }
        });
    }
}
