package com.zeapo.pwdstore;

import android.accessibilityservice.AccessibilityService;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.Toast;

import com.zeapo.pwdstore.utils.PasswordItem;
import com.zeapo.pwdstore.utils.PasswordRepository;

import org.apache.commons.io.FileUtils;
import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class AutofillService extends AccessibilityService {
    private OpenPgpServiceConnection serviceConnection;
    private SharedPreferences settings;
    private AccessibilityNodeInfo info; // the original source of the event (the edittext field)
    private ArrayList<PasswordItem> items;
    private static AutofillService service;

    public final class Constants {
        public static final String TAG = "Keychain";
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        serviceConnection = new OpenPgpServiceConnection(AutofillService.this, "org.sufficientlysecure.keychain");
        serviceConnection.bindToService();
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        service = this;
    }

    public static AutofillService getService() {
        return service;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!event.isPassword() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return;
        }
        info = event.getSource();
        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = packageManager.getApplicationInfo(event.getPackageName().toString(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            applicationInfo = null;
        }
        String appName = (applicationInfo != null ? packageManager.getApplicationLabel(applicationInfo) : "").toString();
        if (appName.equals("OpenKeychain")) {
            return;
        }
        items = recursiveFilter(appName, null);
        if (items.isEmpty()) {
            return;
        }
        ArrayList<CharSequence> itemNames = new ArrayList<>();
        for (PasswordItem item : items) {
            itemNames.add(item.toString());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_AppCompat_Light_Dialog_Alert);
        builder.setNegativeButton("Cancel", null);
        builder.setView(R.layout.autofill_layout);
        final AlertDialog dialog = builder.create();

        dialog.setTitle("Fill with Password Store");
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        dialog.show();
        ((Button) dialog.findViewById(R.id.button)).setText(itemNames.get(0).toString());
        dialog.findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                decryptAndVerify();
                dialog.dismiss();
            }
        });
    }

    private ArrayList<PasswordItem> recursiveFilter(String filter, File dir) {
        ArrayList<PasswordItem> items = new ArrayList<>();
        if (!PasswordRepository.isInitialized()) {
            return items;
        }
        ArrayList<PasswordItem> passwordItems = dir == null ?
                PasswordRepository.getPasswords() :
                PasswordRepository.getPasswords(dir);
        for (PasswordItem item : passwordItems) {
            if (item.getType() == PasswordItem.TYPE_CATEGORY) {
                recursiveFilter(filter, item.getFile());
            }
            if (item.toString().toLowerCase().contains(filter.toLowerCase())) {
                items.add(item);
            }
        }
        return items;
    }

    @Override
    public void onInterrupt() {

    }

    public void decryptAndVerify() {
        Intent data = new Intent();
        data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        InputStream is = null;
        try {
            is = FileUtils.openInputStream(items.get(0).getFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();

        OpenPgpApi api = new OpenPgpApi(AutofillService.this, serviceConnection.getService());
        Intent result = api.executeApi(data, is, os);
        switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
            case OpenPgpApi.RESULT_CODE_SUCCESS: {
                try {
                    String[] passContent = os.toString("UTF-8").split("\n");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        // if the user focused on something else, take focus back
                        // but this will open another dialog...
                        info.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        Bundle args = new Bundle();
                        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                passContent[0]);
                        info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    } else {
                        info.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("autofill_pm", passContent[0]);
                        clipboard.setPrimaryClip(clip);
                        info.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                        clip = ClipData.newPlainText("autofill_pm", "MyPasswordIsDaBest!");
                        clipboard.setPrimaryClip(clip);
                        if (settings.getBoolean("clear_clipboard_20x", false)) {
                            for (int i = 0; i < 19; i++) {
                                clip = ClipData.newPlainText(String.valueOf(i), String.valueOf(i));
                                clipboard.setPrimaryClip(clip);
                            }
                        }
                    }
                } catch (UnsupportedEncodingException e) {
                    Log.e(Constants.TAG, "UnsupportedEncodingException", e);
                }
                break;
            }
            case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED: {
                Log.i("PgpHandler", "RESULT_CODE_USER_INTERACTION_REQUIRED");
                PendingIntent pi = result.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
                // need to start a blank activity to call startIntentSenderForResult
                Intent intent = new Intent(AutofillService.this, AutofillActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                intent.putExtra("pending_intent", pi);
                startActivity(intent);
                break;
            }
            case OpenPgpApi.RESULT_CODE_ERROR: {
                OpenPgpError error = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
                Toast.makeText(AutofillService.this,
                        "Error from OpenKeyChain : " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
                Log.e(Constants.TAG, "onError getErrorId:" + error.getErrorId());
                Log.e(Constants.TAG, "onError getMessage:" + error.getMessage());
                break;
            }
        }
    }
}
