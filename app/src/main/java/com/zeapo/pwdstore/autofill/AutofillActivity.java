package com.zeapo.pwdstore.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import com.zeapo.pwdstore.PasswordStore;

import org.eclipse.jgit.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// blank activity started by service for calling startIntentSenderForResult
public class AutofillActivity extends AppCompatActivity {
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;
    public static final int REQUEST_CODE_PICK = 777;
    public static final int REQUEST_CODE_PICK_MATCH_WITH = 778;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();

        if (extras != null && extras.containsKey("pending_intent")) {
            try {
                PendingIntent pi = extras.getParcelable("pending_intent");
                if (pi == null) {
                    return;
                }
                startIntentSenderForResult(pi.getIntentSender()
                        , REQUEST_CODE_DECRYPT_AND_VERIFY, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                Log.e(AutofillService.Constants.TAG, "SendIntentException", e);
            }
        } else if (extras != null && extras.containsKey("pick")) {
            Intent intent = new Intent(getApplicationContext(), PasswordStore.class);
            intent.putExtra("matchWith", true);
            startActivityForResult(intent, REQUEST_CODE_PICK);
        } else if (extras != null && extras.containsKey("pickMatchWith")) {
            Intent intent = new Intent(getApplicationContext(), PasswordStore.class);
            intent.putExtra("matchWith", true);
            startActivityForResult(intent, REQUEST_CODE_PICK_MATCH_WITH);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        finish();   // go back to the password field app
        switch (requestCode) {
            case REQUEST_CODE_DECRYPT_AND_VERIFY:
                if (resultCode == RESULT_OK) {
                    AutofillService.getInstance().setResultData(data);    // report the result to service
                }
                break;
            case REQUEST_CODE_PICK:
                if (resultCode == RESULT_OK) {
                    AutofillService.getInstance().setPickedPassword(data.getStringExtra("path"));
                }
                break;
            case REQUEST_CODE_PICK_MATCH_WITH:
                if (resultCode == RESULT_OK) {
                    // need to not only decrypt the picked password, but also
                    // update the "match with" preference
                    Bundle extras = getIntent().getExtras();
                    String packageName = extras.getString("packageName");
                    boolean isWeb = extras.getBoolean("isWeb");

                    String path = data.getStringExtra("path");
                    AutofillService.getInstance().setPickedPassword(data.getStringExtra("path"));

                    SharedPreferences prefs;
                    if (!isWeb) {
                        prefs = getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
                    } else {
                        prefs = getApplicationContext().getSharedPreferences("autofill_web", Context.MODE_PRIVATE);
                    }
                    SharedPreferences.Editor editor = prefs.edit();
                    String preference = prefs.getString(packageName, "");
                    switch (preference) {
                        case "":
                        case "/first":
                        case "/never":
                            editor.putString(packageName, path);
                            break;
                        default:
                            List<String> matches = new ArrayList<>(Arrays.asList(preference.trim().split("\n")));
                            matches.add(path);
                            String paths = StringUtils.join(matches, "\n");
                            editor.putString(packageName, paths);
                    }
                    editor.apply();
                }
                break;
        }
    }
}
