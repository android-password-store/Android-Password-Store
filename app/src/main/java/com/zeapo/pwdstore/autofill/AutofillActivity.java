package com.zeapo.pwdstore.autofill;

import android.app.DialogFragment;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// blank activity started by service for calling startIntentSenderForResult
public class AutofillActivity extends AppCompatActivity {
    public static final int REQUEST_CODE_DECRYPT_AND_VERIFY = 9913;
    private boolean bound = false;

    private RecyclerView recyclerView;
    AutofillRecyclerAdapter recyclerAdapter; // let fragment have access
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
        setContentView(R.layout.autofill_recycler_view);
        recyclerView = (RecyclerView) findViewById(R.id.autofill_recycler);

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        // apps for which the user has custom settings should be in the recycler
        final PackageManager pm = getPackageManager();
        SharedPreferences prefs
                = getSharedPreferences("autofill", Context.MODE_PRIVATE);
        Map<String, ?> prefApps = prefs.getAll();
        ArrayList<ApplicationInfo> apps = new ArrayList<>();
        for (String packageName : prefApps.keySet()) {
            try {
                apps.add(pm.getApplicationInfo(packageName, 0));
            } catch (PackageManager.NameNotFoundException e) {
                // remove invalid entries (from uninstalled apps?)
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove(packageName).apply();
            }
        }
        recyclerAdapter = new AutofillRecyclerAdapter(apps, pm, this);
        recyclerView.setAdapter(recyclerAdapter);

        // show the search bar by default but don't open the keyboard
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        final SearchView searchView = (SearchView) findViewById(R.id.app_search);
        searchView.clearFocus();

        // create search suggestions of apps with icons & names
        final SimpleCursorAdapter.ViewBinder viewBinder = new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view instanceof TextView) {
                    ((TextView) view).setText(cursor.getString(columnIndex));
                } else if (view instanceof ImageView) {
                    try {
                        ((ImageView) view).setImageDrawable(pm.getApplicationIcon(cursor.getString(columnIndex)));
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
                return true;
            }
        };

        final List<ApplicationInfo> allApps = pm.getInstalledApplications(0);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                // should be a better/faster way to do this?
                MatrixCursor matrixCursor = new MatrixCursor(new String[]{"_id", "package", "label"});
                for (ApplicationInfo applicationInfo : allApps) {
                    // exclude apps that already have settings; the search is just for adding
                    if (applicationInfo.loadLabel(pm).toString().toLowerCase().contains(newText.toLowerCase())
                            && !recyclerAdapter.contains(applicationInfo.packageName)) {
                        matrixCursor.addRow(new Object[]{0, applicationInfo.packageName, applicationInfo.loadLabel(pm)});
                    }
                }
                SimpleCursorAdapter simpleCursorAdapter = new SimpleCursorAdapter(AutofillActivity.this
                        , R.layout.app_list_item, matrixCursor, new String[]{"package", "label"}
                        , new int[]{android.R.id.icon1, android.R.id.text1}, 0);
                simpleCursorAdapter.setViewBinder(viewBinder);
                searchView.setSuggestionsAdapter(simpleCursorAdapter);
                return false;
            }
        });

        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                return false;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                Cursor cursor = searchView.getSuggestionsAdapter().getCursor();
                String packageName = cursor.getString(1);
                String appName = cursor.getString(2);

                // similar to what happens in ViewHolder.onClick but position -1
                DialogFragment df = new AutofillFragment();
                Bundle args = new Bundle();
                args.putString("packageName", packageName);
                args.putString("appName", appName);
                args.putInt("position", -1);
                df.setArguments(args);
                df.show(getFragmentManager(), "autofill_dialog");
                return false;
            }
        });

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
