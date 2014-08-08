package com.zeapo.pwdstore.crypto;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.UserPreference;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.apache.commons.io.FileUtils;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import org.openintents.openpgp.util.OpenPgpUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class PgpHandler extends Activity {


    private OpenPgpServiceConnection mServiceConnection;
    private String keyIDs = "";
    private String accountName = "";
    SharedPreferences settings;

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


        // some persistance
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        String providerPackageName = settings.getString("openpgp_provider_list", "");
        accountName = settings.getString("openpgp_account_name", "");

        if (TextUtils.isEmpty(providerPackageName)) {
            Toast.makeText(this, "No OpenPGP Provider selected!", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, UserPreference.class);
            startActivity(intent);

        }

        // bind to service
        mServiceConnection = new OpenPgpServiceConnection(
                PgpHandler.this, providerPackageName);
        mServiceConnection.bindToService();


        Bundle extra = getIntent().getExtras();
        if (extra.getString("Operation").equals("DECRYPT")) {
            setContentView(R.layout.decrypt_layout);
            ((TextView) findViewById(R.id.crypto_password_file)).setText(extra.getString("NAME"));
        } else if (extra.getString("Operation").equals("ENCRYPT")) {
            setContentView(R.layout.encrypt_layout);
            String cat = extra.getString("FILE_PATH");
            cat = cat.replace(PasswordRepository.getWorkTree().getAbsolutePath(), "");
            cat = cat + "/";
            ((TextView) findViewById(R.id.crypto_password_category)).setText(cat);
        } else if (extra.getString("Operation").equals("GET_KEY_ID")) {
            // wait until the service is bound
            while (!mServiceConnection.isBound());
            getKeyIds(new Intent());
        }

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
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
        switch (id) {
            case android.R.id.home:
                setResult(RESULT_OK);
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void handleClick(View view) {
        switch (view.getId()) {
            case R.id.crypto_show_button:
                decryptAndVerify(new Intent());
                break;
            case R.id.crypto_confirm_add:
                encrypt(new Intent());
                break;
            case R.id.crypto_cancel_add:
                finish();
                break;
            case R.id.crypto_delete_button:
                deletePassword();
            default:
                // should not happen

        }
//        getKeyIds(new Intent());
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
                Log.e(Constants.TAG, "  " + error.toString());
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

    public class DelayShow extends AsyncTask<Void, Integer, Boolean> {
        int count = 0;
        final int SHOW_TIME = 10;
        ProgressBar pb;

        @Override
        protected void onPreExecute() {
            LinearLayout container = (LinearLayout) findViewById(R.id.crypto_container);
            container.setVisibility(View.VISIBLE);

            TextView extraText = (TextView) findViewById(R.id.crypto_extra_show);

            if (extraText.getText().length() != 0)
                ((LinearLayout) findViewById(R.id.crypto_extra_show_layout)).setVisibility(View.VISIBLE);

            this.pb = (ProgressBar) findViewById(R.id.pbLoading);
            this.pb.setMax(SHOW_TIME);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            while (this.count < SHOW_TIME) {
                SystemClock.sleep(1000);
                this.count++;
                publishProgress(this.count);
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean b) {
            //clear password
            ((TextView) findViewById(R.id.crypto_password_show)).setText("");
            findViewById(R.id.crypto_extra_show_layout).setVisibility(View.INVISIBLE);
            findViewById(R.id.crypto_container).setVisibility(View.INVISIBLE);
        }


        @Override
        protected void onProgressUpdate(Integer... values) {
            this.pb.setProgress(values[0]);
        }

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
                case REQUEST_CODE_ENCRYPT: {
                    encrypt(data);
                    break;
                }
                case REQUEST_CODE_DECRYPT_AND_VERIFY: {
                    decryptAndVerify(data);
                    break;
                }
            }
        }
    }

    public class PgpCallback implements OpenPgpApi.IOpenPgpCallback {
        boolean returnToCiphertextField;
        ByteArrayOutputStream os;
        int requestCode;

        private PgpCallback(boolean returnToCiphertextField, ByteArrayOutputStream os, int requestCode) {
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
                    if (requestCode == REQUEST_CODE_DECRYPT_AND_VERIFY && os != null) {
                        try {
                            Log.d(OpenPgpApi.TAG, "result: " + os.toByteArray().length
                                    + " str=" + os.toString("UTF-8"));

                            if (returnToCiphertextField) {
                                findViewById(R.id.crypto_container).setVisibility(View.VISIBLE);

                                String[] passContent = os.toString("UTF-8").split("\n");
                                ((TextView) findViewById(R.id.crypto_password_show))
                                        .setText(passContent[0]);

                                String extraContent = os.toString("UTF-8").replaceFirst(".*\n", "");
                                if (extraContent.length() != 0) {
                                    ((TextView) findViewById(R.id.crypto_extra_show))
                                            .setText(extraContent);
                                }
                                new DelayShow().execute();
                            } else {
                                showToast(os.toString());
                            }
                        } catch (UnsupportedEncodingException e) {
                            Log.e(Constants.TAG, "UnsupportedEncodingException", e);
                        }
                    }
                    // encrypt
                    if (requestCode == REQUEST_CODE_ENCRYPT && os != null) {
                        try {
                            String path = getIntent().getExtras().getString("FILE_PATH")
                                    + "/" + ((EditText) findViewById(R.id.crypto_password_file_edit)).getText().toString()
                                    + ".gpg";
                            OutputStream outputStream = FileUtils.openOutputStream(new File(path));
                            outputStream.write(os.toByteArray());
                            Intent data = new Intent();
                            data.putExtra("CREATED_FILE", path);
                            data.putExtra("NAME", ((EditText) findViewById(R.id.crypto_password_file_edit)).getText().toString());
                            setResult(RESULT_OK, data);
                            finish();
                        } catch (Exception e) {
                            Log.e(Constants.TAG, "UnsupportedEncodingException", e);
                        }
                    }

                    // get key ids
                    if (result.hasExtra(OpenPgpApi.RESULT_KEY_IDS)) {
                        long[] ids = result.getLongArrayExtra(OpenPgpApi.RESULT_KEY_IDS);

                        for (int i = 0; i < ids.length; i++) {
                            keyIDs += OpenPgpUtils.convertKeyIdToHex(ids[i]) + ", ";
                        }
                        settings.edit().putString("openpgp_key_ids", keyIDs);
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


    public void getKeyIds(Intent data) {
        accountName = settings.getString("openpgp_account_name", "");

        data.setAction(OpenPgpApi.ACTION_GET_KEY_IDS);
        data.putExtra(OpenPgpApi.EXTRA_USER_IDS, new String[]{accountName});

        OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());

        api.executeApiAsync(data, null, null, new PgpCallback(false, null, PgpHandler.REQUEST_CODE_GET_KEY_IDS));
    }

    public void decryptAndVerify(Intent data) {
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);

        try {
            InputStream is = FileUtils.openInputStream(new File(getIntent().getExtras().getString("FILE_PATH")));

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
            api.executeApiAsync(data, is, os, new PgpCallback(true, os, REQUEST_CODE_DECRYPT_AND_VERIFY));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void encrypt(Intent data) {
        accountName = settings.getString("openpgp_account_name", "");

        if (accountName.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage("Please set your OpenKeychain account (email) in the preferences")
                    .setTitle("Account name empty!")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            try {
                                Intent intent = new Intent(getApplicationContext(), UserPreference.class);
                                startActivity(intent);
                            } catch (Exception e) {
                                System.out.println("Exception caught :(");
                                e.printStackTrace();
                            }
                        }
                    }).setNegativeButton("No thanks", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    // Do nothing...
                }
            }).show();
        } else {

            data.setAction(OpenPgpApi.ACTION_ENCRYPT);
            data.putExtra(OpenPgpApi.EXTRA_USER_IDS, new String[]{accountName});
            data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);

            String name = ((EditText) findViewById(R.id.crypto_password_file_edit)).getText().toString();
            String pass = ((EditText) findViewById(R.id.crypto_password_edit)).getText().toString();
            String extra = ((EditText) findViewById(R.id.crypto_extra_edit)).getText().toString();

            if (name.isEmpty()) {
                showToast("Please provide a file name");
                return;
            }

            if (pass.isEmpty() || extra.isEmpty()) {
                showToast("You cannot use an empty password or empty extra content");
                return;
            }

            ByteArrayInputStream is;

            try {
                is = new ByteArrayInputStream((pass + "\n" + extra).getBytes("UTF-8"));

                ByteArrayOutputStream os = new ByteArrayOutputStream();

                OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
                api.executeApiAsync(data, is, os, new PgpCallback(true, os, REQUEST_CODE_ENCRYPT));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private void deletePassword() {
        new AlertDialog.Builder(this).
                setMessage("Are you sure you want to delete the password \"" +
                        getIntent().getExtras().getString("NAME") + "\"")
                .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        (new File(getIntent().getExtras().getString("FILE_PATH"))).delete();
                        setResult(RESULT_OK);
                        finish();
                    }
                })
                .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .show();
    }
}
