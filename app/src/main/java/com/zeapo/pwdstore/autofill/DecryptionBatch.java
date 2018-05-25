package com.zeapo.pwdstore.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.zeapo.pwdstore.PasswordEntry;
import com.zeapo.pwdstore.autofill_legacy.AutofillActivity;

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
import java.util.List;

public class DecryptionBatch {
    List<File> items;
    List<PasswordEntry> results;
    Integer processedCount = 0;
    Context applicationContext;
    OnBatchDecryptedListener onBatchDecryptedListener;

    private static final String TAG = "PasswordStoreAutofillRequest";

    public DecryptionBatch(
            Context applicationContext,
            List<File> items,
            OnBatchDecryptedListener onBatchDecryptedListener) {
        this.results = new ArrayList<>();
        this.applicationContext = applicationContext;
        this.items = items;
        this.onBatchDecryptedListener = onBatchDecryptedListener;
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



    private void onDecrypted() {

    }

    private void decrypt(File item, IOpenPgpService2 service) {
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
                    final PasswordEntry entry = new PasswordEntry(os);
                    this.results.add(entry);
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "UnsupportedEncodingException", e);
                }
                break;
            }
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                Log.i("PgpHandler", "RESULT_CODE_USER_INTERACTION_REQUIRED");
                PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                // need to start a blank activity to call startIntentSenderForResult
                Intent intent = new Intent(applicationContext, AutofillActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("pending_intent", pi);
                applicationContext.startActivity(intent);
                break;
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
        processedCount++;
        if (results.size() == items.size()) {
            onBatchDecryptedListener.onBatchDecrypted(results);
        }
    }

    public void decryptBatch() {
        for (final File file: items) {
            class PgpOnBoundListener implements OpenPgpServiceConnection.OnBound {
                @Override
                public void onBound(IOpenPgpService2 service) {
                    decrypt(file, service);
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to bin to OpenPGP Service");
                }
            }
            bindOpenPgpService(new PgpOnBoundListener());
        }
    }
}
