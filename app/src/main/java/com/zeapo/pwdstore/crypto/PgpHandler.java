package com.zeapo.pwdstore.crypto;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.primitives.Longs;
import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.UserPreference;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.apache.commons.io.FileUtils;
import org.openintents.openpgp.IOpenPgpService;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class PgpHandler extends AppCompatActivity implements OpenPgpServiceConnection.OnBound{


    private OpenPgpServiceConnection mServiceConnection;
    private Set<String> keyIDs = new HashSet<>();
    SharedPreferences settings;
    private Activity activity;
    ClipboardManager clipboard;

    private boolean registered;

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

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        this.activity = this;
        this.clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        // some persistance
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        String providerPackageName = settings.getString("openpgp_provider_list", "");
        keyIDs = settings.getStringSet("openpgp_key_ids_set", new HashSet<>());

        registered = false;

        if (TextUtils.isEmpty(providerPackageName)) {
            Toast.makeText(this, this.getResources().getString(R.string.provider_toast_text), Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, UserPreference.class);
            startActivity(intent);
            // a small hack to avoid eternal loop later, have to be solved via a startactivityforresult()
            setResult(RESULT_CANCELED);
            finish();

        } else {

            // bind to service
            mServiceConnection = new OpenPgpServiceConnection(
                    PgpHandler.this, providerPackageName, this);
            mServiceConnection.bindToService();
            registered = true;
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onStop(){
        super.onStop();
        if (this.registered && this.mServiceConnection.isBound())
            try {
                this.mServiceConnection.unbindFromService();
            } catch (Exception e){

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
        switch (id) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                finish();
                return true;
            case R.id.copy_password:
                copyToClipBoard();
        }
        return super.onOptionsItemSelected(item);
    }

    public void copyToClipBoard() {

        if (findViewById(R.id.crypto_password_show) == null)
            return;

        ClipData clip = ClipData.newPlainText("pgp_handler_result_pm", ((TextView) findViewById(R.id.crypto_password_show)).getText());
        clipboard.setPrimaryClip(clip);
        try {
            showToast(this.getResources().getString(R.string.clipboard_beginning_toast_text)
                    + " " + Integer.parseInt(settings.getString("general_show_time", "45")) + " "
                    + this.getResources().getString(R.string.clipboard_ending_toast_text));
        } catch (NumberFormatException e)
        {
            showToast(this.getResources().getString(R.string.clipboard_beginning_toast_text)
                    + " 45 "
                    + this.getResources().getString(R.string.clipboard_ending_toast_text));
        }
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
//                deletePassword();
                break;
            case R.id.crypto_get_key_ids:
                getKeyIds(new Intent());
                break;
            default:
                // should not happen

        }
    }

    private void handleError(final OpenPgpError error) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(PgpHandler.this,
                        "Error from OpenKeyChain : " + error.getMessage(),
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

    public class DelayShow extends AsyncTask<Void, Integer, Boolean> {
        ProgressBar pb;

        @Override
        protected void onPreExecute() {
            LinearLayout container = (LinearLayout) findViewById(R.id.crypto_container);
            container.setVisibility(View.VISIBLE);

            TextView extraText = (TextView) findViewById(R.id.crypto_extra_show);

            if (extraText.getText().length() != 0)
                ((LinearLayout) findViewById(R.id.crypto_extra_show_layout)).setVisibility(View.VISIBLE);

            this.pb = (ProgressBar) findViewById(R.id.pbLoading);

			// Make Show Time a user preference
			// kLeZ: Changed to match the default for pass
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(container.getContext());
            int SHOW_TIME;
            try {
                SHOW_TIME = Integer.parseInt(settings.getString("general_show_time", "45"));
            } catch (NumberFormatException e) {
                SHOW_TIME = 45;
            }
            this.pb.setMax(SHOW_TIME);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
			while (this.pb.getProgress() < this.pb.getMax())
			{
                SystemClock.sleep(1000);
				publishProgress(this.pb.getProgress() + 1);
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean b) {

            ClipData clip = ClipData.newPlainText("pgp_handler_result_pm", "MyPasswordIsDaBest!");
            clipboard.setPrimaryClip(clip);

            //clear password
            ((TextView) findViewById(R.id.crypto_password_show)).setText("");
            ((TextView) findViewById(R.id.crypto_extra_show)).setText("");
            findViewById(R.id.crypto_extra_show_layout).setVisibility(View.INVISIBLE);
            findViewById(R.id.crypto_container).setVisibility(View.INVISIBLE);
            activity.setResult(RESULT_CANCELED);
            activity.finish();
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
                case REQUEST_CODE_GET_KEY_IDS:
                    getKeyIds(data);
                    break;
            }
        } else if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED, data);
            finish();
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
                    // encrypt/decrypt/sign/verify
                    if (requestCode == REQUEST_CODE_DECRYPT_AND_VERIFY && os != null) {
                        try {
                            if (returnToCiphertextField) {
                                findViewById(R.id.crypto_container).setVisibility(View.VISIBLE);
                                Typeface type = Typeface.createFromAsset(getAssets(), "fonts/sourcecodepro.ttf");
                                String[] passContent = os.toString("UTF-8").split("\n");
                                ((TextView) findViewById(R.id.crypto_password_show))
                                        .setTypeface(type);
                                ((TextView) findViewById(R.id.crypto_password_show))
                                        .setText(passContent[0]);

                                String extraContent = os.toString("UTF-8").replaceFirst(".*\n", "");
                                if (extraContent.length() != 0) {
                                    ((TextView) findViewById(R.id.crypto_extra_show))
                                            .setTypeface(type);
                                    ((TextView) findViewById(R.id.crypto_extra_show))
                                            .setText(extraContent);
                                }
                                new DelayShow().execute();
                                if (settings.getBoolean("copy_on_decrypt", true)) {
                                    copyToClipBoard();
                                }
                            } else {
                                Log.d("PGPHANDLER", "Error message after decrypt : " + os.toString());
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
                            outputStream.close();
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
                        Set<String> keys = new HashSet<String>();

                        for (long id : ids) keys.add(String.valueOf(id)); // use Long
                        settings.edit().putStringSet("openpgp_key_ids_set", keys).apply();

                        showToast("PGP key selected");
                        setResult(RESULT_OK);
                        finish();
                    }
                    break;
                }
                case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                    Log.i("PgpHandler", "RESULT_CODE_USER_INTERACTION_REQUIRED");

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
                    // TODO show what kind of error it is
                    /* For example:
                     * No suitable key found -> no key in OpenKeyChain
                     *
                     * Check in open-pgp-lib how their definitions and error code
                     */
                    OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                    handleError(error);

                    break;
                }

            }
        }
    }


    public void getKeyIds(Intent data) {
        data.setAction(OpenPgpApi.ACTION_GET_KEY_IDS);
        OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
        api.executeApiAsync(data, null, null, new PgpCallback(false, null, PgpHandler.REQUEST_CODE_GET_KEY_IDS));
    }

    public void decryptAndVerify(Intent data) {
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);

        try {
            InputStream is = FileUtils.openInputStream(new File(getIntent().getExtras().getString("FILE_PATH")));

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
            api.executeApiAsync(data, is, os, new PgpCallback(true, os, REQUEST_CODE_DECRYPT_AND_VERIFY));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Encrypts a password file
     * @param data
     */
    public void encrypt(Intent data) {
        data.setAction(OpenPgpApi.ACTION_ENCRYPT);

        ArrayList<Long> longKeys = new ArrayList<>();
        for (String keyId : keyIDs) longKeys.add(Long.valueOf(keyId));
        data.putExtra(OpenPgpApi.EXTRA_KEY_IDS, Longs.toArray(longKeys));

        data.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);

        String name = ((EditText) findViewById(R.id.crypto_password_file_edit)).getText().toString();
        String pass = ((EditText) findViewById(R.id.crypto_password_edit)).getText().toString();
        String extra = ((EditText) findViewById(R.id.crypto_extra_edit)).getText().toString();

        if (name.isEmpty()) {
            showToast(this.getResources().getString(R.string.file_toast_text));
            return;
        }

        if (pass.isEmpty() && extra.isEmpty()) {
            showToast(this.getResources().getString(R.string.empty_toast_text));
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

    @Override
    public void onBound(IOpenPgpService service) {
        Log.i("PGP", "ISBOUND!!");

        Bundle extra = getIntent().getExtras();
        if (extra.getString("Operation").equals("DECRYPT")) {
            setContentView(R.layout.decrypt_layout);
            ((TextView) findViewById(R.id.crypto_password_file)).setText(extra.getString("NAME"));
            String cat = new File(extra.getString("FILE_PATH").replace(PasswordRepository.getWorkTree().getAbsolutePath(), ""))
                    .getParentFile().getName();

            ((TextView) findViewById(R.id.crypto_password_category)).setText(cat + "/");
            decryptAndVerify(new Intent());
        } else if (extra.getString("Operation").equals("ENCRYPT")) {
            setContentView(R.layout.encrypt_layout);
            String cat = extra.getString("FILE_PATH");
            cat = cat.replace(PasswordRepository.getWorkTree().getAbsolutePath(), "");
            cat = cat + "/";
            ((TextView) findViewById(R.id.crypto_password_category)).setText(cat);
        } else if (extra.getString("Operation").equals("GET_KEY_ID")) {
            getKeyIds(new Intent());

//            setContentView(R.layout.key_id);
//            if (!keyIDs.isEmpty()) {
//                String keys = keyIDs.split(",").length > 1 ? keyIDs : keyIDs.split(",")[0];
//                ((TextView) findViewById(R.id.crypto_key_ids)).setText(keys);
//            }
        }
    }

    @Override
    public void onError(Exception e) {

    }

}
