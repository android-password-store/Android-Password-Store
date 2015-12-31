package com.zeapo.pwdstore.autofill;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.zeapo.pwdstore.PasswordStore;

// blank activity started by service for calling startIntentSenderForResult
public class AutofillActivity extends AppCompatActivity {
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;
    public static final int REQUEST_CODE_MATCH_WITH = 777;

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
        } else if (extras != null && extras.containsKey("matchWith")) {
            Intent intent = new Intent(getApplicationContext(), PasswordStore.class);
            intent.putExtra("matchWith", true);
            startActivityForResult(intent, REQUEST_CODE_MATCH_WITH);
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
            case REQUEST_CODE_MATCH_WITH:
                if (resultCode == RESULT_OK) {
                    AutofillService.getInstance().setPickedPassword(data.getStringExtra("path"));
                }
        }
    }
}
