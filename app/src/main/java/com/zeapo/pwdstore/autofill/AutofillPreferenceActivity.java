package com.zeapo.pwdstore.autofill;

import android.app.DialogFragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AutofillPreferenceActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    AutofillRecyclerAdapter recyclerAdapter; // let fragment have access
    private RecyclerView.LayoutManager layoutManager;

    private PackageManager pm;

    private boolean recreate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.autofill_recycler_view);
        recyclerView = (RecyclerView) findViewById(R.id.autofill_recycler);

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));

        pm = getPackageManager();

        new populateTask().execute();

        setTitle("Autofill Apps");
    }

    private class populateTask extends AsyncTask<Void, Void, List<ResolveInfo>> {
        @Override
        protected void onPreExecute() {
            findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);
        }

        @Override
        protected List<ResolveInfo> doInBackground(Void... params) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> allApps = pm.queryIntentActivities(intent, 0);
            Collections.sort(allApps, new Comparator<ResolveInfo>() {
                @Override
                public int compare(ResolveInfo lhs, ResolveInfo rhs) {
                    return lhs.loadLabel(pm).toString().compareTo(rhs.loadLabel(pm).toString());
                }
            });
            return allApps;
        }

        @Override
        protected void onPostExecute(List<ResolveInfo> allApps) {
            findViewById(R.id.progress_bar).setVisibility(View.GONE);

            recyclerAdapter = new AutofillRecyclerAdapter(new ArrayList<>(allApps), pm, AutofillPreferenceActivity.this);
            recyclerView.setAdapter(recyclerAdapter);

            recreate = false;
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                recreate = true;
                recyclerView.scrollToPosition(recyclerAdapter.getPosition(extras.getString("packageName")));
                showDialog(extras.getString("packageName"), extras.getString("appName"));
            }
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
                recyclerAdapter.filter(s);
                return true;
            }
        });

        // When using the support library, the setOnActionExpandListener() method is
        // static and accepts the MenuItem object as an argument
        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
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

    public void showDialog(String packageName, String appName) {
        DialogFragment df = new AutofillFragment();
        Bundle args = new Bundle();
        args.putString("packageName", packageName);
        args.putString("appName", appName);
        args.putInt("position", recyclerAdapter.getPosition(packageName));
        df.setArguments(args);
        df.show(getFragmentManager(), "autofill_dialog");
    }
}
