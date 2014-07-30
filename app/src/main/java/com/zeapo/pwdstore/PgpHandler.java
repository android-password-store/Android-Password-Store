package com.zeapo.pwdstore;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.zeapo.pwdstore.R;

import org.apache.commons.io.FileUtils;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import org.openintents.openpgp.util.OpenPgpUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class PgpHandler extends Activity {


    private OpenPgpServiceConnection mServiceConnection;

    public static final int REQUEST_CODE_SIGN = 9910;
    public static final int REQUEST_CODE_ENCRYPT = 9911;
    public static final int REQUEST_CODE_SIGN_AND_ENCRYPT = 9912;
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;
    public static final int REQUEST_CODE_GET_KEY = 9914;
    public static final int REQUEST_CODE_GET_KEY_IDS = 9915;

    public final class Constants {
        public static final String TAG = "Keychain";
    }

    private String filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pgp_handler);

        // some persistance
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String providerPackageName = settings.getString("openpgp_provider_list", "");

        System.out.println(getIntent().getExtras().getString("FILE_PATH"));
        Bundle extra = getIntent().getExtras();
        ((TextView) findViewById(R.id.hhh)).setText(extra.getString("FILE_PATH"));

        if (TextUtils.isEmpty(providerPackageName)) {
            Toast.makeText(this, "No OpenPGP Provider selected!", Toast.LENGTH_LONG).show();
            finish();
        } else {
            // bind to service
            mServiceConnection = new OpenPgpServiceConnection(
                    PgpHandler.this, providerPackageName);
            mServiceConnection.bindToService();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.pgp_handler, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void handleError(final OpenPgpError error) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(PgpHandler.this,
                        "onError id:" + error.getErrorId() + "\n\n" + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                Log.e(Constants.TAG, "onError getErrorId:" + error.getErrorId());
                Log.e(Constants.TAG, "onError getMessage:" + error.getMessage());
            }
        });
    }

    private void showToast(final String message) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(PgpHandler.this,
                        message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class MyCallback implements OpenPgpApi.IOpenPgpCallback {
        boolean returnToCiphertextField;
        ByteArrayOutputStream os;
        int requestCode;

        private MyCallback(boolean returnToCiphertextField, ByteArrayOutputStream os, int requestCode) {
            this.returnToCiphertextField = returnToCiphertextField;
            this.os = os;
            this.requestCode = requestCode;
        }

        @Override
        public void onReturn(Intent result) {
            switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                case OpenPgpApi.RESULT_CODE_SUCCESS: {
                    showToast("RESULT_CODE_SUCCESS");

                    // encrypt/decrypt/sign/verify
                    if (os != null) {
                        try {
                            Log.d(OpenPgpApi.TAG, "result: " + os.toByteArray().length
                                    + " str=" + os.toString("UTF-8"));
                        } catch (UnsupportedEncodingException e) {
                            Log.e(Constants.TAG, "UnsupportedEncodingException", e);
                        }
                    }

                    // verify
                    if (result.hasExtra(OpenPgpApi.RESULT_SIGNATURE)) {
                        OpenPgpSignatureResult sigResult
                                = result.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
                        showToast(sigResult.toString());
                    }

                    // get key ids
                    if (result.hasExtra(OpenPgpApi.RESULT_KEY_IDS)) {
                        long[] keyIds = result.getLongArrayExtra(OpenPgpApi.RESULT_KEY_IDS);
                        String out = "keyIds: ";
                        for (int i = 0; i < keyIds.length; i++) {
                            out += OpenPgpUtils.convertKeyIdToHex(keyIds[i]) + ", ";
                        }

                        showToast(out);
                    }
                    break;
                }
                case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                    showToast("RESULT_CODE_USER_INTERACTION_REQUIRED");

                    PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                    try {
                        PgpHandler.this.startIntentSenderForResult(pi.getIntentSender(),
                                requestCode, null, 0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e(Constants.TAG, "SendIntentException", e);
                    }
                    break;
                }
                case OpenPgpApi.RESULT_CODE_ERROR: {
                    showToast("RESULT_CODE_ERROR");

                    OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                    handleError(error);
                    break;
                }
            }
        }
    }

    public void getKey(View view) {
        decryptAndVerify(new Intent());
    }

    public void decryptAndVerify(Intent data) {
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        data.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, "Mohamed Zenadi");

        try {
            InputStream is = FileUtils.openInputStream(new File(getIntent().getExtras().getString("FILE_PATH")));

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
            api.executeApiAsync(data, is, os, new MyCallback(false, os, REQUEST_CODE_DECRYPT_AND_VERIFY));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
