package com.zeapo.pwdstore;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

// blank activity started by service for calling startIntentSenderForResult
public class AutofillActivity extends AppCompatActivity {
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;
    private boolean bound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        PendingIntent pi = intent.getExtras().getParcelable("pending_intent");
        try {
            startIntentSenderForResult(pi.getIntentSender()
                    , REQUEST_CODE_DECRYPT_AND_VERIFY, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
            Log.e(AutofillService.Constants.TAG, "SendIntentException", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        finish();   // go back to the password field app
        if (resultCode == RESULT_OK) {
            AutofillService.setUnlockOK();    // report the result to service
        }
    }
}
