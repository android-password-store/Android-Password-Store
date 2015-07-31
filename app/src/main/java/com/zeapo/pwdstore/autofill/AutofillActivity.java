package com.zeapo.pwdstore.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// blank activity started by service for calling startIntentSenderForResult
public class AutofillActivity extends AppCompatActivity {
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;
    private boolean bound = false;

    private RecyclerView recyclerView;
    private AutofillRecyclerAdapter recyclerAdapter;
    private RecyclerView.LayoutManager layoutManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        // if called by service just for startIntentSenderForResult
        if (extras != null) {
            try {
                PendingIntent pi = intent.getExtras().getParcelable("pending_intent");
                if (pi == null) {
                    return;
                }
                startIntentSenderForResult(pi.getIntentSender()
                        , REQUEST_CODE_DECRYPT_AND_VERIFY, null, 0, 0, 0);
            } catch (IntentSender.SendIntentException e) {
                Log.e(AutofillService.Constants.TAG, "SendIntentException", e);
            }
            return;
        }
        // otherwise if called from settings
        final PackageManager pm = getPackageManager();
        List<ApplicationInfo> allApps = pm.getInstalledApplications(0);

        SharedPreferences prefs
                = getSharedPreferences("autofill", Context.MODE_PRIVATE);
        Map<String, ?> prefApps = prefs.getAll();
        ArrayList<ApplicationInfo> apps = new ArrayList<>();
        for (ApplicationInfo applicationInfo : allApps) {
            if (prefApps.containsKey(applicationInfo.packageName)) {
                apps.add(applicationInfo);
            }
        }

        setContentView(R.layout.autofill_recycler_view);
        recyclerView = (RecyclerView) findViewById(R.id.autofill_recycler);

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        recyclerAdapter = new AutofillRecyclerAdapter(apps, pm);
        recyclerView.setAdapter(recyclerAdapter);

        setTitle("Autofill Apps");

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        finish();   // go back to the password field app
        if (resultCode == RESULT_OK) {
            AutofillService.setUnlockOK();    // report the result to service
        }
    }
}
