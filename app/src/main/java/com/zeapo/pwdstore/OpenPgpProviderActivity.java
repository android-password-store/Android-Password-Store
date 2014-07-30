/*
 * Copyright (C) 2013-2014 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import org.openintents.openpgp.util.OpenPgpUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class OpenPgpProviderActivity extends Activity {
    private EditText mMessage;
    private EditText mCiphertext;
    private EditText mEncryptUserIds;
    private Button mSign;
    private Button mEncrypt;
    private Button mSignAndEncrypt;
    private Button mDecryptAndVerify;
    private EditText mAccount;
    private EditText mGetKeyEdit;
    private EditText mGetKeyIdsEdit;
    private Button mGetKey;
    private Button mGetKeyIds;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.openpgp_provider);

        mMessage = (EditText) findViewById(R.id.crypto_provider_demo_message);
        mCiphertext = (EditText) findViewById(R.id.crypto_provider_demo_ciphertext);
        mEncryptUserIds = (EditText) findViewById(R.id.crypto_provider_demo_encrypt_user_id);
        mSign = (Button) findViewById(R.id.crypto_provider_demo_sign);
        mEncrypt = (Button) findViewById(R.id.crypto_provider_demo_encrypt);
        mSignAndEncrypt = (Button) findViewById(R.id.crypto_provider_demo_sign_and_encrypt);
        mDecryptAndVerify = (Button) findViewById(R.id.crypto_provider_demo_decrypt_and_verify);
        mAccount = (EditText) findViewById(R.id.crypto_provider_demo_account);
        mGetKeyEdit = (EditText) findViewById(R.id.crypto_provider_demo_get_key_edit);
        mGetKeyIdsEdit = (EditText) findViewById(R.id.crypto_provider_demo_get_key_ids_edit);
        mGetKey = (Button) findViewById(R.id.crypto_provider_demo_get_key);
        mGetKeyIds = (Button) findViewById(R.id.crypto_provider_demo_get_key_ids);

        mSign.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sign(new Intent());
            }
        });
        mEncrypt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                encrypt(new Intent());
            }
        });
        mSignAndEncrypt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                signAndEncrypt(new Intent());
            }
        });
        mDecryptAndVerify.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                decryptAndVerify(new Intent());
            }
        });
        mGetKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getKey(new Intent());
            }
        });
        mGetKeyIds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getKeyIds(new Intent());
            }
        });

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String providerPackageName = settings.getString("openpgp_provider_list", "");
        if (TextUtils.isEmpty(providerPackageName)) {
            Toast.makeText(this, "No OpenPGP Provider selected!", Toast.LENGTH_LONG).show();
            finish();
        } else {
            // bind to service
            mServiceConnection = new OpenPgpServiceConnection(
                    OpenPgpProviderActivity.this, providerPackageName);
            mServiceConnection.bindToService();
        }
    }

    private void handleError(final OpenPgpError error) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(OpenPgpProviderActivity.this,
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
                Toast.makeText(OpenPgpProviderActivity.this,
                        message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Takes input from message or ciphertext EditText and turns it into a ByteArrayInputStream
     *
     * @param ciphertext
     * @return
     */
    private InputStream getInputstream(boolean ciphertext) {
        InputStream is = null;
        try {
            String inputStr;
            if (ciphertext) {
                inputStr = mCiphertext.getText().toString();
            } else {
                inputStr = mMessage.getText().toString();
            }
            is = new ByteArrayInputStream(inputStr.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            Log.e(Constants.TAG, "UnsupportedEncodingException", e);
        }

        return is;
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

                            if (returnToCiphertextField) {
                                mCiphertext.setText(os.toString("UTF-8"));
                            } else {
                                mMessage.setText(os.toString("UTF-8"));
                            }
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
                        OpenPgpProviderActivity.this.startIntentSenderForResult(pi.getIntentSender(),
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

    public void sign(Intent data) {
        data.setAction(OpenPgpApi.ACTION_SIGN);
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        data.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, mAccount.getText().toString());

        InputStream is = getInputstream(false);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
        api.executeApiAsync(data, is, os, new MyCallback(true, os, REQUEST_CODE_SIGN));
    }

    public void encrypt(Intent data) {
        data.setAction(OpenPgpApi.ACTION_ENCRYPT);
        data.putExtra(OpenPgpApi.EXTRA_USER_IDS, mEncryptUserIds.getText().toString().split(","));
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        data.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, mAccount.getText().toString());

        InputStream is = getInputstream(false);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
        api.executeApiAsync(data, is, os, new MyCallback(true, os, REQUEST_CODE_ENCRYPT));
    }

    public void signAndEncrypt(Intent data) {
        data.setAction(OpenPgpApi.ACTION_SIGN_AND_ENCRYPT);
        data.putExtra(OpenPgpApi.EXTRA_USER_IDS, mEncryptUserIds.getText().toString().split(","));
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        data.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, mAccount.getText().toString());

        InputStream is = getInputstream(false);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
        api.executeApiAsync(data, is, os, new MyCallback(true, os, REQUEST_CODE_SIGN_AND_ENCRYPT));
    }

    public void decryptAndVerify(Intent data) {
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        data.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, mAccount.getText().toString());

        InputStream is = getInputstream(true);
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
        api.executeApiAsync(data, is, os, new MyCallback(false, os, REQUEST_CODE_DECRYPT_AND_VERIFY));
    }

    public void getKey(Intent data) {
        data.setAction(OpenPgpApi.ACTION_GET_KEY);
        data.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, mAccount.getText().toString());
        data.putExtra(OpenPgpApi.EXTRA_KEY_ID, Long.decode(mGetKeyEdit.getText().toString()));

        OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
        api.executeApiAsync(data, null, null, new MyCallback(false, null, REQUEST_CODE_GET_KEY));
    }

    public void getKeyIds(Intent data) {
        data.setAction(OpenPgpApi.ACTION_GET_KEY_IDS);
        data.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, mAccount.getText().toString());
        data.putExtra(OpenPgpApi.EXTRA_USER_IDS, mGetKeyIdsEdit.getText().toString().split(","));

        OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
        api.executeApiAsync(data, null, null, new MyCallback(false, null, REQUEST_CODE_GET_KEY_IDS));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(Constants.TAG, "onActivityResult resultCode: " + resultCode);

        // try again after user interaction
        if (resultCode == RESULT_OK) {
            /*
             * The data originally given to one of the methods above, is again
             * returned here to be used when calling the method again after user
             * interaction. The Intent now also contains results from the user
             * interaction, for example selected key ids.
             */
            switch (requestCode) {
                case REQUEST_CODE_SIGN: {
                    sign(data);
                    break;
                }
                case REQUEST_CODE_ENCRYPT: {
                    encrypt(data);
                    break;
                }
                case REQUEST_CODE_SIGN_AND_ENCRYPT: {
                    signAndEncrypt(data);
                    break;
                }
                case REQUEST_CODE_DECRYPT_AND_VERIFY: {
                    decryptAndVerify(data);
                    break;
                }
                case REQUEST_CODE_GET_KEY: {
                    getKey(data);
                    break;
                }
                case REQUEST_CODE_GET_KEY_IDS: {
                    getKeyIds(data);
                    break;
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mServiceConnection != null) {
            mServiceConnection.unbindFromService();
        }
    }

}