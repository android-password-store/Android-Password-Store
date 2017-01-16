package com.zeapo.pwdstore.autofill;

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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.util.Log;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AutofillService extends AccessibilityService {
    private static AutofillService instance;
    private OpenPgpServiceConnection serviceConnection;
    private SharedPreferences settings;
    private AccessibilityNodeInfo info; // the original source of the event (the edittext field)
    private ArrayList<File> items; // password choices
    private int lastWhichItem;
    private AlertDialog dialog;
    private AccessibilityWindowInfo window;
    private Intent resultData = null; // need the intent which contains results from user interaction
    private CharSequence packageName;
    private boolean ignoreActionFocus = false;
    private String webViewTitle = null;
    private String webViewURL = null;

    public final class Constants {
        public static final String TAG = "Keychain";
    }

    public static AutofillService getInstance() {
        return instance;
    }

    public void setResultData(Intent data) { resultData = data; }

    public void setPickedPassword(String path) {
        items.add(new File(PasswordRepository.getRepositoryDirectory(getApplicationContext()) + "/" + path + ".gpg"));
        bindDecryptAndVerify();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

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
        // TODO there should be a better way of disabling service
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return;
        }

        // if returning to the source app from a successful AutofillActivity
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getPackageName() != null && event.getPackageName().equals(packageName)
                && resultData != null) {
            bindDecryptAndVerify();
        }

        // look for webView and trigger accessibility events if window changes
        // or if page changes in chrome
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && event.getPackageName() != null
                    && (event.getPackageName().equals("com.android.chrome")
                        || event.getPackageName().equals("com.android.browser")))) {
            // there is a chance for getRootInActiveWindow() to return null at any time. save it.
            AccessibilityNodeInfo root = getRootInActiveWindow();
            webViewTitle = searchWebView(root);
            webViewURL = null;
            if (webViewTitle != null) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar");
                if (nodes.isEmpty()) {
                    nodes = root.findAccessibilityNodeInfosByViewId("com.android.browser:id/url");
                }
                for (AccessibilityNodeInfo node : nodes)
                    if (node.getText() != null) {
                        try {
                            webViewURL = new URL(node.getText().toString()).getHost();
                        } catch (MalformedURLException e) {
                            if (e.toString().contains("Protocol not found")) {
                                try {
                                    webViewURL = new URL("http://" + node.getText().toString()).getHost();
                                } catch (MalformedURLException ignored) {
                                }
                            }
                        }
                    }
            }
        }

        // nothing to do if not password field focus, field is keychain app
        if (!event.isPassword()
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getPackageName() != null && event.getPackageName().equals("org.sufficientlysecure.keychain")
                || event.getPackageName() != null && event.getPackageName().equals("com.android.systemui")) {
            dismissDialog(event);
            return;
        }

        if (dialog != null && dialog.isShowing()) {
            // the current dialog must belong to this window; ignore clicks on this password field
            // why handle clicks at all then? some cases e.g. Paypal there is no initial focus event
            if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                return;
            }
            // if it was not a click, the field was refocused or another field was focused; recreate
            dialog.dismiss();
            dialog = null;
        }

        // ignore the ACTION_FOCUS from decryptAndVerify otherwise dialog will appear after Fill
        if (ignoreActionFocus) {
            ignoreActionFocus = false;
            return;
        }

        // need to request permission before attempting to draw dialog
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        }

        // we are now going to attempt to fill, save AccessibilityNodeInfo for later in decryptAndVerify
        // (there should be a proper way to do this, although this seems to work 90% of the time)
        info = event.getSource();

        // save the dialog's corresponding window so we can use getWindows() in dismissDialog
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window = info.getWindow();
        }

        String packageName;
        String appName;
        boolean isWeb;

        // Match with the app if a webview was not found or one was found but
        // there's no title or url to go by
        if (webViewTitle == null || (webViewTitle.equals("") && webViewURL == null)) {
            packageName = info.getPackageName().toString();

            // get the app name and find a corresponding password
            PackageManager packageManager = getPackageManager();
            ApplicationInfo applicationInfo;
            try {
                applicationInfo = packageManager.getApplicationInfo(event.getPackageName().toString(), 0);
            } catch (PackageManager.NameNotFoundException e) {
                applicationInfo = null;
            }
            appName = (applicationInfo != null ? packageManager.getApplicationLabel(applicationInfo) : "").toString();

            isWeb = false;

            setAppMatchingPasswords(appName, packageName);
        } else {
            // now we may have found a title but webViewURL could be null
            // we set packagename so that we can find the website setting entry
            packageName = setWebMatchingPasswords(webViewTitle, webViewURL);
            appName = packageName;
            isWeb = true;
        }

        // if autofill_always checked, show dialog even if no matches (automatic
        // or otherwise)
        if (items.isEmpty() && !settings.getBoolean("autofill_always", false)) {
            return;
        }
        showDialog(packageName, appName, isWeb);
    }

    private String searchWebView(AccessibilityNodeInfo source) {
        if (source == null) {
            return null;
        }
        for (int i = 0; i < source.getChildCount(); i++) {
            AccessibilityNodeInfo u = source.getChild(i);
            if (u == null) {
                continue;
            }
            if (u.getClassName() != null && u.getClassName().equals("android.webkit.WebView")) {
                if (u.getContentDescription() != null) {
                    return u.getContentDescription().toString();
                }
                return "";
            }
            if (searchWebView(u) != null) {
                return searchWebView(u);
            }
            u.recycle();
        }
        return null;
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
            dismiss = !(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    event.getPackageName() != null &&
                    event.getPackageName().toString().contains("inputmethod"));
        }
        if (dismiss && dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            dialog = null;
        }
    }

    private String setWebMatchingPasswords(String webViewTitle, String webViewURL) {
        // Return the URL needed to open the corresponding Settings.
        String settingsURL = webViewURL;

        // if autofill_default is checked and prefs.getString DNE, 'Automatically match with password'/"first" otherwise "never"
        String defValue = settings.getBoolean("autofill_default", true) ? "/first" : "/never";
        SharedPreferences prefs;
        String preference;

        prefs = getSharedPreferences("autofill_web", Context.MODE_PRIVATE);
        preference = defValue;
        if (webViewURL != null) {
            Map<String, ?> prefsMap = prefs.getAll();
            for (String key : prefsMap.keySet()) {
                // for websites unlike apps there can be blank preference of "" which
                // means use default, so ignore it.
                if ((webViewURL.toLowerCase().contains(key.toLowerCase()) || key.toLowerCase().contains(webViewURL.toLowerCase()))
                        && !prefs.getString(key, null).equals("")) {
                    preference = prefs.getString(key, null);
                    settingsURL = key;
                }
            }
        }

        switch (preference) {
            case "/first":
                if (!PasswordRepository.isInitialized()) {
                    PasswordRepository.initialize(this);
                }
                items = searchPasswords(PasswordRepository.getRepositoryDirectory(this), webViewTitle);
                break;
            case "/never":
                items = new ArrayList<>();
                break;
            default:
                getPreferredPasswords(preference);
        }

        return settingsURL;
    }

    private void setAppMatchingPasswords(String appName, String packageName) {
        // if autofill_default is checked and prefs.getString DNE, 'Automatically match with password'/"first" otherwise "never"
        String defValue = settings.getBoolean("autofill_default", true) ? "/first" : "/never";
        SharedPreferences prefs;
        String preference;

        prefs = getSharedPreferences("autofill", Context.MODE_PRIVATE);
        preference = prefs.getString(packageName, defValue);

        switch (preference) {
            case "/first":
                if (!PasswordRepository.isInitialized()) {
                    PasswordRepository.initialize(this);
                }
                items = searchPasswords(PasswordRepository.getRepositoryDirectory(this), appName);
                break;
            case "/never":
                items = new ArrayList<>();
                break;
            default:
                getPreferredPasswords(preference);
        }
    }

    // Put the newline separated list of passwords from the SharedPreferences
    // file into the items list.
    private void getPreferredPasswords(String preference) {
        if (!PasswordRepository.isInitialized()) {
            PasswordRepository.initialize(this);
        }
        String preferredPasswords[] = preference.split("\n");
        items = new ArrayList<>();
        for (String password : preferredPasswords) {
            String path = PasswordRepository.getRepositoryDirectory(getApplicationContext()) + "/" + password + ".gpg";
            if (new File(path).exists()) {
                items.add(new File(path));
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
                if (appName.toLowerCase().contains(file.getName().toLowerCase().replace(".gpg", ""))) {
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

    private void showDialog(final String packageName, final String appName, final boolean isWeb) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog);
        builder.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                dialog.dismiss();
                dialog = null;
            }
        });
        builder.setNeutralButton("Settings", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {    //TODO make icon? gear?
                // the user will have to return to the app themselves.
                Intent intent = new Intent(AutofillService.this, AutofillPreferenceActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra("packageName", packageName);
                intent.putExtra("appName", appName);
                intent.putExtra("isWeb", isWeb);
                startActivity(intent);
            }
        });

        // populate the dialog items, always with pick + pick and match. Could
        // make it optional (or make height a setting for the same effect)
        CharSequence itemNames[] = new CharSequence[items.size() + 2];
        for (int i = 0; i < items.size(); i++) {
            itemNames[i] = items.get(i).getName().replace(".gpg", "");
        }
        itemNames[items.size()] = "Pick...";
        itemNames[items.size() + 1] = "Pick and match...";
        builder.setItems(itemNames, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                lastWhichItem = which;
                if (which < items.size()) {
                    bindDecryptAndVerify();
                } else if (which == items.size()){
                    Intent intent = new Intent(AutofillService.this, AutofillActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.putExtra("pick", true);
                    startActivity(intent);
                } else {
                    lastWhichItem--;    // will add one element to items, so lastWhichItem=items.size()+1
                    Intent intent = new Intent(AutofillService.this, AutofillActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    intent.putExtra("pickMatchWith", true);
                    intent.putExtra("packageName", packageName);
                    intent.putExtra("isWeb", isWeb);
                    startActivity(intent);
                }
            }
        });

        dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        // arbitrary non-annoying size
        int height = 154;
        if (itemNames.length > 1) {
            height += 46;
        }
        dialog.getWindow().setLayout((int) (240 * getApplicationContext().getResources().getDisplayMetrics().density)
                , (int) (height * getApplicationContext().getResources().getDisplayMetrics().density));
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
        // TODO we are dropping frames, (did we before??) find out why and maybe make this async
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

                        clip = ClipData.newPlainText("autofill_pm", "");
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
