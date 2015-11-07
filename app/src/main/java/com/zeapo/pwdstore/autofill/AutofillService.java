package com.zeapo.pwdstore.autofill;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.TypedValue;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.utils.PasswordRepository;

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

public class AutofillService extends AccessibilityService {
    private OpenPgpServiceConnection serviceConnection;
    private SharedPreferences settings;
    private AccessibilityNodeInfo info; // the original source of the event (the edittext field)
    private ArrayList<File> items; // password choices
    private int lastWhichItem;
    private AlertDialog dialog;
    private AccessibilityWindowInfo window;
    private static Intent resultData = null; // need the intent which contains results from user interaction
    private CharSequence packageName;
    private boolean ignoreActionFocus = false;

    public final class Constants {
        public static final String TAG = "Keychain";
    }

    public static void setResultData(Intent data) { resultData = data; }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        serviceConnection = new OpenPgpServiceConnection(AutofillService.this
                , "org.sufficientlysecure.keychain");
        serviceConnection.bindToService();
        settings = PreferenceManager.getDefaultSharedPreferences(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SYSTEM_ALERT_WINDOW)
                == PackageManager.PERMISSION_DENIED) {
            // may need a way to request the permission but only activities can, so by notification?
            return;
        }

        // if returning to the source app from a successful AutofillActivity
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName().equals(packageName) && resultData != null) {
            bindDecryptAndVerify();
        }

        // need to see if window has a WebView every time, so future events are sent?
        AccessibilityNodeInfo source = event.getSource();
        if (source == null) {
            return;
        }
        searchWebView(source);

        // nothing to do if not password field focus, android version, or field is keychain app
        if (!event.isPassword()
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2
                || event.getPackageName().equals("org.sufficientlysecure.keychain")) {
            dismissDialog(event);
            source.recycle();   // is this necessary???
            return;
        }

        if (dialog != null && dialog.isShowing()) {
            // the current dialog must belong to this window; ignore clicks on this password field
            // why handle clicks at all then? some cases e.g. Paypal there is no initial focus event
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                source.recycle();
                return;
            }
            // if it was not a click, the field was refocused or another field was focused; recreate
            dialog.dismiss();
            dialog = null;
        }

        // ignore the ACTION_FOCUS from decryptAndVerify otherwise dialog will appear after Fill
        if (ignoreActionFocus) {
            ignoreActionFocus = false;
            source.recycle();
            return;
        }

        // we are now going to attempt to fill, save AccessibilityNodeInfo for later in decryptAndVerify
        // (there should be a proper way to do this, although this seems to work 90% of the time)
        info = source;

        // save the dialog's corresponding window so we can use getWindows() in dismissDialog
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window = info.getWindow();
        }

        // get the app name and find a corresponding password
        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = packageManager.getApplicationInfo(event.getPackageName().toString(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            applicationInfo = null;
        }
        final String appName = (applicationInfo != null ? packageManager.getApplicationLabel(applicationInfo) : "").toString();

        setMatchingPasswords(appName, info.getPackageName().toString());
        if (items.isEmpty()) {
            return;
        }

        showDialog(appName);
    }

    private boolean searchWebView(AccessibilityNodeInfo source) {
        for (int i = 0; i < source.getChildCount(); i++) {
            AccessibilityNodeInfo u = source.getChild(i);
            if (u == null) {
                continue;
            }
            // this is not likely to always work
            if (u.getContentDescription() != null && u.getContentDescription().equals("Web View")) {
                return true;
            }
            if (searchWebView(u)) {
                return true;
            }
            u.recycle();
        }
        return false;
    }

    // dismiss the dialog if the window has changed
    private void dismissDialog(AccessibilityEvent event) {
        // the default keyboard showing/hiding is a window state changed event
        // on Android 5+ we can use getWindows() to determine when the original window is not visible
        // on Android 4.3 we have to use window state changed events and filter out the keyboard ones
        // there may be other exceptions...
        boolean dismiss;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            dismiss = !getWindows().contains(window);
        } else {
            dismiss = !(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && event.getPackageName().toString().contains("inputmethod"));
        }
        if (dismiss && dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private void setMatchingPasswords(String appName, String packageName) {
        // if autofill_default is checked and prefs.getString DNE, 'Automatically match with password'/"first" otherwise "never"
        String defValue = settings.getBoolean("autofill_default", true) ? "/first" : "/never";
        SharedPreferences prefs = getSharedPreferences("autofill", Context.MODE_PRIVATE);
        String preference = prefs.getString(packageName, defValue);
        switch (preference) {
            case "/first":
                if (!PasswordRepository.isInitialized()) {
                    PasswordRepository.initialize(this);
                }
                items = searchPasswords(PasswordRepository.getRepositoryDirectory(this), appName);
                break;
            case "/never":
                items.clear();
                return;
            default:
                if (!PasswordRepository.isInitialized()) {
                    PasswordRepository.initialize(this);
                }
                String preferred[] = preference.split("\n");
                items = new ArrayList<>();
                for (String prefer : preferred) {
                    String path = PasswordRepository.getWorkTree() + "/" + prefer + ".gpg";
                    if (new File(path).exists()) {
                        items.add(new File(path));
                    }
                }
        }
    }

    private ArrayList<File> searchPasswords(File path, String appName) {
        ArrayList<File> passList
                = PasswordRepository.getFilesList(path);

        if (passList.size() == 0) return new ArrayList<>();

        ArrayList<File> items = new ArrayList<>();

        for (File file : passList) {
            if (file.isFile()) {
                if (file.toString().toLowerCase().contains(appName.toLowerCase())) {
                    items.add(file);
                }
            } else {
                // ignore .git directory
                if (file.getName().equals(".git"))
                    continue;
                items.addAll(searchPasswords(file, appName));
            }
        }
        return items;
    }

    private void showDialog(final String appName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog);
        builder.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                dialog.dismiss();
                dialog = null;
            }
        });
//        builder.setPositiveButton(R.string.autofill_fill, new DialogInterface.OnClickListener() {
//            @Override
//            public void onClick(DialogInterface dialog, int which) {
//                bindDecryptAndVerify();
//            }
//        });
        builder.setNeutralButton("Settings", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {    //TODO make icon? gear?
                // the user will have to return to the app themselves.
                Intent intent = new Intent(AutofillService.this, AutofillPreferenceActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("packageName", info.getPackageName());
                intent.putExtra("appName", appName);
                startActivity(intent);
            }
        });
        CharSequence itemNames[] = new CharSequence[items.size()];
        for (int i = 0; i < items.size(); i++) {
            itemNames[i] = items.get(i).getName().replace(".gpg", "");
        }

        builder.setItems(itemNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == items.size()) {
                    // the user will have to return to the app themselves.
                    Intent intent = new Intent(AutofillService.this, AutofillPreferenceActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.putExtra("packageName", info.getPackageName());
                    intent.putExtra("appName", appName);
                    startActivity(intent);
                } else {
                    lastWhichItem = which;
                    bindDecryptAndVerify();
                }
            }
        });
        dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        // arbitrary non-annoying size
        int height = 160;
        if (items.size() > 1) {
            height += 48;
        }
        dialog.getWindow().setLayout((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 240, getResources().getDisplayMetrics())
                , (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, getResources().getDisplayMetrics()));
        dialog.show();
    }

    @Override
    public void onInterrupt() {

    }

    private class onBoundListener implements OpenPgpServiceConnection.OnBound {
        @Override
        public void onBound(IOpenPgpService2 service) {
            decryptAndVerify();
        }
        @Override
        public void onError(Exception e) {
            e.printStackTrace();
        }
    }

    private void bindDecryptAndVerify() {
        if (serviceConnection.getService() == null) {
            // the service was disconnected, need to bind again
            // give it a listener and in the callback we will decryptAndVerify
            serviceConnection = new OpenPgpServiceConnection(AutofillService.this
                    , "org.sufficientlysecure.keychain", new onBoundListener());
            serviceConnection.bindToService();
        } else {
            decryptAndVerify();
        }
    }

    private void decryptAndVerify() {
        packageName = info.getPackageName();
        Intent data;
        if (resultData == null) {
            data = new Intent();
            data.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
        } else {
            data = resultData;
            resultData = null;
        }
        InputStream is = null;
        try {
            is = FileUtils.openInputStream(items.get(lastWhichItem));
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

                    // if the user focused on something else, take focus back
                    // but this will open another dialog...hack to ignore this
                    // & need to ensure performAction correct (i.e. what is info now?)
                    ignoreActionFocus = info.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Bundle args = new Bundle();
                        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                passContent[0]);
                        info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                    } else {
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
                    info.recycle();
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
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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
