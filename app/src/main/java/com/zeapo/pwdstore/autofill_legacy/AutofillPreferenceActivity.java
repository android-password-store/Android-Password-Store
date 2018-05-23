package com.zeapo.pwdstore.autofill_legacy;

import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AutofillPreferenceActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    AutofillRecyclerAdapter recyclerAdapter; // let fragment have access

    private PackageManager pm;

    private boolean recreate; // flag for action on up press; origin autofill dialog? different act

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.autofill_recycler_view);
        recyclerView = (RecyclerView) findViewById(R.id.autofill_recycler);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        pm = getPackageManager();

        new populateTask().execute();

        // if the preference activity was started from the autofill dialog
        recreate = false;
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            recreate = true;

            showDialog(extras.getString("packageName"), extras.getString("appName"), extras.getBoolean("isWeb"));
        }

        setTitle("Autofill Apps");

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog("", "", true);
            }
        });
    }

    private class populateTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            runOnUiThread(new Runnable() {
                public void run() {
                    findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);
                }
            });
        }

        @Override
        protected Void doInBackground(Void... params) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> allAppsResolveInfo = pm.queryIntentActivities(intent, 0);
            List<AutofillRecyclerAdapter.AppInfo> allApps = new ArrayList<>();

            for (ResolveInfo app : allAppsResolveInfo) {
                allApps.add(new AutofillRecyclerAdapter.AppInfo(app.activityInfo.packageName
                        , app.loadLabel(pm).toString(), false, app.loadIcon(pm)));
            }

            SharedPreferences prefs = getSharedPreferences("autofill_web", Context.MODE_PRIVATE);
            Map<String, ?> prefsMap = prefs.getAll();
            for (String key : prefsMap.keySet()) {
                try {
                    allApps.add(new AutofillRecyclerAdapter.AppInfo(key, key, true, pm.getApplicationIcon("com.android.browser")));
                } catch (PackageManager.NameNotFoundException e) {
                    allApps.add(new AutofillRecyclerAdapter.AppInfo(key, key, true, null));
                }
            }

            recyclerAdapter = new AutofillRecyclerAdapter(allApps, pm, AutofillPreferenceActivity.this);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            runOnUiThread(new Runnable() {
                public void run() {
                    findViewById(R.id.progress_bar).setVisibility(View.GONE);
                    recyclerView.setAdapter(recyclerAdapter);
                    Bundle extras = getIntent().getExtras();
                    if (extras != null) {
                        recyclerView.scrollToPosition(recyclerAdapter.getPosition(extras.getString("appName")));
                    }
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.autofill_preference, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (recyclerAdapter != null) {
                    recyclerAdapter.filter(s);
                }
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // in service, we CLEAR_TASK. then we set the recreate flag.
            // something of a hack, but w/o CLEAR_TASK, behaviour was unpredictable
            case android.R.id.home:
                Intent upIntent = NavUtils.getParentActivityIntent(this);
                if (recreate) {
                    TaskStackBuilder.create(this)
                            .addNextIntentWithParentStack(upIntent)
                            .startActivities();
                } else {
                    NavUtils.navigateUpTo(this, upIntent);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showDialog(String packageName, String appName, boolean isWeb) {
        DialogFragment df = new AutofillFragment();
        Bundle args = new Bundle();
        args.putString("packageName", packageName);
        args.putString("appName", appName);
        args.putBoolean("isWeb", isWeb);
        df.setArguments(args);
        df.show(getFragmentManager(), "autofill_dialog");
    }
}
