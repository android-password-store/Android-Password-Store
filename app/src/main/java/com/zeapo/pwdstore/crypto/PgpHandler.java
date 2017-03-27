package com.zeapo.pwdstore.crypto;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.primitives.Longs;
import com.zeapo.pwdstore.BuildConfig;
import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.SelectFolderFragment;
import com.zeapo.pwdstore.UserPreference;
import com.zeapo.pwdstore.pwgenDialogFragment;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.apache.commons.io.FileUtils;
import org.openintents.openpgp.IOpenPgpService2;
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

public class PgpHandler extends AppCompatActivity implements OpenPgpServiceConnection.OnBound {
    private static DelayShow delayTask;
    private OpenPgpServiceConnection mServiceConnection;
    private Set<String> keyIDs = new HashSet<>();
    SharedPreferences settings;
    private Activity activity;
    ClipboardManager clipboard;

    SelectFolderFragment passwordList;
    private Intent selectFolderData;

    private boolean registered;

    public static final int REQUEST_CODE_SIGN = 9910;
    public static final int REQUEST_CODE_ENCRYPT = 9911;
    public static final int REQUEST_CODE_SIGN_AND_ENCRYPT = 9912;
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;
    public static final int REQUEST_CODE_GET_KEY = 9914;
    public static final int REQUEST_CODE_GET_KEY_IDS = 9915;
    public static final int REQUEST_CODE_EDIT = 9916;
    public static final int REQUEST_CODE_SELECT_FOLDER = 9917;

    private String decodedPassword = "";

    public final class Constants {
        public static final String TAG = "Keychain";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        this.activity = this;
        this.clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        if (getIntent().getStringExtra("Operation").equals("ENCRYPT")) {
            setTitle("New password");
        }

        // some persistance
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        String providerPackageName = settings.getString("openpgp_provider_list", "");
        keyIDs = settings.getStringSet("openpgp_key_ids_set", new HashSet<String>());

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
    public void onStop() {
        super.onStop();
        if (this.registered && this.mServiceConnection.isBound())
            try {
                this.mServiceConnection.unbindFromService();
            } catch (Exception e) {

            }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        switch (getIntent().getStringExtra("Operation")){
            case "ENCRYPT":
                getMenuInflater().inflate(R.menu.pgp_handler_new_password, menu);
                break;
            case "SELECTFOLDER":
                getMenuInflater().inflate(R.menu.pgp_handler_select_folder, menu);
                break;
            default:
                getMenuInflater().inflate(R.menu.pgp_handler, menu);
        }
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
                break;
            case R.id.share_password_as_plaintext:
                shareAsPlaintext();
                break;
            case R.id.edit_password:
                editPassword();
                break;
            case R.id.crypto_confirm_add:
                encrypt(new Intent());
                break;
            case R.id.crypto_cancel_add:
                setResult(RESULT_CANCELED);
                finish();
                return true;
            case R.id.crypto_select:
                selectFolder();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void selectFolder() {
        if (selectFolderData == null || passwordList == null){
            Log.wtf(Constants.TAG,"Folder selected while the app didn't ask for one to be selected?");
        }
        selectFolderData.putExtra("SELECTED_FOLDER_PATH",passwordList.getCurrentDir().getAbsolutePath());
        setResult(RESULT_OK,selectFolderData);
        finish();
    }

    public void editPassword() {
        // if in encrypt or in decrypt and password is invisible
        // (because !showPassword, so this will instantly close), do nothing
        if (findViewById(R.id.crypto_password_show) == null
                || findViewById(R.id.crypto_container).getVisibility() != View.VISIBLE)
            return;

        CharSequence category = ((TextView) findViewById(R.id.crypto_password_category)).getText();
        CharSequence file = ((TextView) findViewById(R.id.crypto_password_file)).getText();
        CharSequence password = ((TextView) findViewById(R.id.crypto_password_show)).getText();
        CharSequence extra = ((TextView) findViewById(R.id.crypto_extra_show)).getText();

        setContentView(R.layout.encrypt_layout);
        Typeface monoTypeface = Typeface.createFromAsset(getAssets(), "fonts/sourcecodepro.ttf");
        ((EditText) findViewById(R.id.crypto_password_edit)).setTypeface(monoTypeface);
        ((EditText) findViewById(R.id.crypto_extra_edit)).setTypeface(monoTypeface);

        ((TextView) findViewById(R.id.crypto_password_category)).setText(category);
        ((EditText) findViewById(R.id.crypto_password_file_edit)).setText(file);
        ((EditText) findViewById(R.id.crypto_password_edit)).setText(password);
        ((EditText) findViewById(R.id.crypto_extra_edit)).setText(extra);

        // strictly editing only i.e. can't save this password's info to another file by changing name
        findViewById(R.id.crypto_password_file_edit).setEnabled(false);

        // the original intent was to decrypt so FILE_PATH will have the file, not enclosing dir
        // PgpCallback expects the dir when encrypting
        String filePath = getIntent().getExtras().getString("FILE_PATH");
        String directoryPath = filePath.substring(0, filePath.lastIndexOf(File.separator));
        Intent intent = new Intent(this, PgpHandler.class);
        intent.putExtra("FILE_PATH", directoryPath);
        intent.putExtra("Operation", "ENCRYPT");
        intent.putExtra("fromDecrypt", true);
        setIntent(intent);

        // recreate the options menu to be the encrypt one
        invalidateOptionsMenu();
    }

    public void shareAsPlaintext() {

        if (findViewById(R.id.share_password_as_plaintext) == null)
            return;

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, decodedPassword);
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_plaintext_password_to)));//Always show a picker to give the user a chance to cancel
    }

    public void copyToClipBoard() {

        if (findViewById(R.id.crypto_password_show) == null)
            return;

        setTimer();

        ClipData clip = ClipData.newPlainText("pgp_handler_result_pm", decodedPassword);
        clipboard.setPrimaryClip(clip);

        try {
            showToast(this.getResources().getString(R.string.clipboard_beginning_toast_text)
                    + " " + Integer.parseInt(settings.getString("general_show_time", "45")) + " "
                    + this.getResources().getString(R.string.clipboard_ending_toast_text));
        } catch (NumberFormatException e) {
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
            case R.id.crypto_delete_button:
//                deletePassword();
                break;
            case R.id.crypto_get_key_ids:
                getKeyIds(new Intent());
                break;
            case R.id.generate_password:
                DialogFragment df = new pwgenDialogFragment();
                df.show(getFragmentManager(), "generator");
            default:
                Log.wtf(Constants.TAG,"This should not happen.... PgpHandler.java#handleClick(View) default reached.");
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
        boolean clearClipboard = true;
        int current, showTime;

        @Override
        protected void onPreExecute() {
            try {
                showTime = Integer.parseInt(settings.getString("general_show_time", "45"));
            } catch (NumberFormatException e) {
                showTime = 45;
            }
            current = 0;

            LinearLayout container = (LinearLayout) findViewById(R.id.crypto_container);
            container.setVisibility(View.VISIBLE);

            TextView extraText = (TextView) findViewById(R.id.crypto_extra_show);

            if (extraText.getText().length() != 0)
                findViewById(R.id.crypto_extra_show_layout).setVisibility(View.VISIBLE);

            if (showTime == 0) {
                // treat 0 as forever, and the user must exit and/or clear clipboard on their own
                cancel(true);
            } else {
                this.pb = (ProgressBar) findViewById(R.id.pbLoading);
                this.pb.setMax(showTime);
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            while (current < showTime) {
                SystemClock.sleep(1000);
                current++;
                publishProgress(current);
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean b) {
            // only clear the clipboard if we automatically copied the password to it
            if (settings.getBoolean("copy_on_decrypt", true) && clearClipboard) {
                Log.d("DELAY_SHOW", "Clearing the clipboard");
                ClipData clip = ClipData.newPlainText("pgp_handler_result_pm", "");
                clipboard.setPrimaryClip(clip);
                if (settings.getBoolean("clear_clipboard_20x", false)) {
                    Handler handler = new Handler();
                    for (int i = 0; i < 19; i++) {
                        final String count = String.valueOf(i);
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                clipboard.setPrimaryClip(ClipData.newPlainText(count, count));
                            }
                        }, i*500);
                    }
                }
            }
            decodedPassword = "";
            if (findViewById(R.id.crypto_password_show) != null) {
                // clear password; if decrypt changed to encrypt layout via edit button, no need
                ((TextView) findViewById(R.id.crypto_password_show)).setText("");
                ((TextView) findViewById(R.id.crypto_extra_show)).setText("");
                findViewById(R.id.crypto_extra_show_layout).setVisibility(View.INVISIBLE);
                findViewById(R.id.crypto_container).setVisibility(View.INVISIBLE);
                activity.setResult(RESULT_CANCELED);
                activity.finish();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            this.pb.setProgress(values[0]);
        }

        public void setClearClipboard(boolean value) {
            clearClipboard = value;
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
                    findViewById(R.id.progress_bar_label).setVisibility(View.GONE);
                    decryptAndVerify(data);
                    break;
                }
                case REQUEST_CODE_GET_KEY_IDS:
                    getKeyIds(data);
                    break;
                case REQUEST_CODE_EDIT: {
                    edit(data);
                    break;
                }
            }
        } else if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED, data);
            finish();
        }
    }

    private void selectFolder(Intent data) {

        if (data.getStringExtra("Operation") == null || !data.getStringExtra("Operation").equals("SELECTFOLDER")){
            Log.e(Constants.TAG,"PgpHandler#selectFolder(Intent) triggered with incorrect intent.");
            if (BuildConfig.DEBUG){
                throw new UnsupportedOperationException("Triggered with incorrect intent.");
            }
            return;
        }

        Log.d(Constants.TAG,"PgpHandler#selectFolder(Intent).");


        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();


        passwordList = new SelectFolderFragment();
        Bundle args = new Bundle();
        args.putString("Path", PasswordRepository.getRepositoryDirectory(getApplicationContext()).getAbsolutePath());

        passwordList.setArguments(args);

        getSupportActionBar().show();

        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        fragmentTransaction.replace(R.id.pgp_handler_linearlayout, passwordList, "PasswordsList");
        fragmentTransaction.commit();

        this.selectFolderData = data;
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
                    final TextView textViewPassword = (TextView) findViewById(R.id.crypto_password_show);
                    if (requestCode == REQUEST_CODE_DECRYPT_AND_VERIFY && os != null) {
                        try {
                            if (returnToCiphertextField) {
                                findViewById(R.id.progress_bar).setVisibility(View.GONE);
                                findViewById(R.id.progress_bar_label).setVisibility(View.GONE);

                                boolean showPassword = settings.getBoolean("show_password", true);
                                findViewById(R.id.crypto_container).setVisibility(View.VISIBLE);

                                Typeface monoTypeface = Typeface.createFromAsset(getAssets(), "fonts/sourcecodepro.ttf");
                                final String[] passContent = os.toString("UTF-8").split("\n");
                                textViewPassword
                                        .setTypeface(monoTypeface);
                                textViewPassword
                                        .setText(passContent[0]);

                                Button toggleVisibilityButton = (Button) findViewById(R.id.crypto_password_toggle_show);
                                toggleVisibilityButton.setVisibility(showPassword?View.GONE:View.VISIBLE);
                                textViewPassword.setTransformationMethod(showPassword?null:new HoldToShowPasswordTransformation(toggleVisibilityButton, new Runnable() {
                                    @Override
                                    public void run() {
                                        textViewPassword
                                                .setText(passContent[0]);
                                    }
                                }));
                                decodedPassword = passContent[0];

                                String extraContent = os.toString("UTF-8").replaceFirst(".*\n", "");
                                if (extraContent.length() != 0) {
                                    ((TextView) findViewById(R.id.crypto_extra_show))
                                            .setTypeface(monoTypeface);
                                    ((TextView) findViewById(R.id.crypto_extra_show))
                                            .setText(extraContent);
                                }

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
                            // if coming from decrypt screen->edit button
                            if (getIntent().getBooleanExtra("fromDecrypt", false)) {
                                data.putExtra("needCommit", true);
                            }
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

                    // edit
                    if (requestCode == REQUEST_CODE_EDIT && os != null) {
                        try {
                            if (returnToCiphertextField) {
                                findViewById(R.id.progress_bar).setVisibility(View.GONE);
                                findViewById(R.id.progress_bar_label).setVisibility(View.GONE);

                                findViewById(R.id.crypto_container).setVisibility(View.VISIBLE);

                                Typeface monoTypeface = Typeface.createFromAsset(getAssets(), "fonts/sourcecodepro.ttf");
                                String[] passContent = os.toString("UTF-8").split("\n");
                                textViewPassword
                                        .setTypeface(monoTypeface);
                                textViewPassword
                                        .setText(passContent[0]);
                                decodedPassword = passContent[0];

                                String extraContent = os.toString("UTF-8").replaceFirst(".*\n", "");
                                if (extraContent.length() != 0) {
                                    ((TextView) findViewById(R.id.crypto_extra_show))
                                            .setTypeface(monoTypeface);
                                    ((TextView) findViewById(R.id.crypto_extra_show))
                                            .setText(extraContent);
                                }

                                editPassword();
                            } else {
                                Log.d("PGPHANDLER", "Error message after decrypt : " + os.toString());
                            }
                        } catch (UnsupportedEncodingException e) {
                            Log.e(Constants.TAG, "UnsupportedEncodingException", e);
                        }
                    }
                    break;
                }
                case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                    Log.i("PgpHandler", "RESULT_CODE_USER_INTERACTION_REQUIRED");

                    View progress_bar_label = findViewById(R.id.progress_bar_label);
                    if (progress_bar_label != null) {
                        progress_bar_label.setVisibility(View.VISIBLE);
                    }

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

        findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);

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
     *
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

    public void edit(Intent data) {
        // exactly same as decrypt, only we want a different callback
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);

        findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);

        try {
            InputStream is = FileUtils.openInputStream(new File(getIntent().getExtras().getString("FILE_PATH")));

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            OpenPgpApi api = new OpenPgpApi(this, mServiceConnection.getService());
            api.executeApiAsync(data, is, os, new PgpCallback(true, os, REQUEST_CODE_EDIT));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // TODO (low priority but still...) android M potential permissions crashes
    @Override
    public void onBound(IOpenPgpService2 service) {
        Log.i("PGP", "ISBOUND!!");

        Bundle extra = getIntent().getExtras();
        final String operation = extra.getString("Operation");
        if (operation == null){
            return;
        }
        if (operation.equals("DECRYPT")) {
            setContentView(R.layout.decrypt_layout);
            ((TextView) findViewById(R.id.crypto_password_file)).setText(extra.getString("NAME"));
            String path = extra
                    .getString("FILE_PATH")
                    .replace(PasswordRepository.getRepositoryDirectory(getApplicationContext()).getAbsolutePath(), "");
            String cat = new File(path).getParentFile().getName();

            ((TextView) findViewById(R.id.crypto_password_category)).setText(cat + "/");
            decryptAndVerify(new Intent());
        } else if (operation.equals("ENCRYPT")) {
            setContentView(R.layout.encrypt_layout);
            Typeface monoTypeface = Typeface.createFromAsset(getAssets(), "fonts/sourcecodepro.ttf");
            ((EditText) findViewById(R.id.crypto_password_edit)).setTypeface(monoTypeface);
            ((EditText) findViewById(R.id.crypto_extra_edit)).setTypeface(monoTypeface);
            String cat = extra.getString("FILE_PATH");
            cat = cat.replace(PasswordRepository.getRepositoryDirectory(getApplicationContext()).getAbsolutePath(), "");
            cat = cat + "/";
            ((TextView) findViewById(R.id.crypto_password_category)).setText(cat);
        } else if (operation.equals("GET_KEY_ID")) {
            getKeyIds(new Intent());

//            setContentView(R.layout.key_id);
//            if (!keyIDs.isEmpty()) {
//                String keys = keyIDs.split(",").length > 1 ? keyIDs : keyIDs.split(",")[0];
//                ((TextView) findViewById(R.id.crypto_key_ids)).setText(keys);
//            }
        } else if (operation.equals("EDIT")) {
            setContentView(R.layout.decrypt_layout);
            ((TextView) findViewById(R.id.crypto_password_file)).setText(extra.getString("NAME"));
            String cat = new File(extra.getString("FILE_PATH").replace(PasswordRepository.getRepositoryDirectory(getApplicationContext()).getAbsolutePath(), ""))
                    .getParentFile().getName();

            ((TextView) findViewById(R.id.crypto_password_category)).setText(cat + "/");
            edit(new Intent());
        } else if (operation.equals("SELECTFOLDER")){
            setContentView(R.layout.select_folder_layout);
            selectFolder(getIntent());
        }
    }

    @Override
    public void onError(Exception e) {

    }

    private class HoldToShowPasswordTransformation extends PasswordTransformationMethod implements View.OnTouchListener {
        private final Runnable onToggle;
        private boolean shown = false;

        private HoldToShowPasswordTransformation(Button button, Runnable onToggle) {
            this.onToggle = onToggle;
            button.setOnTouchListener(this);
        }

        @Override
        public CharSequence getTransformation(CharSequence charSequence, View view) {
            return shown ? charSequence : super.getTransformation("12345", view);
        }

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    shown = true;
                    onToggle.run();
                    break;
                case MotionEvent.ACTION_UP:
                    shown = false;
                    onToggle.run();
                    break;
            }
            return false;
        }
    }

    private void setTimer() {
        // If a task already exist, let it finish without clearing the clipboard
        if (delayTask != null) {
            delayTask.setClearClipboard(false);
        }

        delayTask = new DelayShow();
        delayTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
