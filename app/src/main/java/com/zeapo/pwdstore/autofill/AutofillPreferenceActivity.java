package com.zeapo.pwdstore.autofill;

import android.app.DialogFragment;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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

import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AutofillPreferenceActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    AutofillRecyclerAdapter recyclerAdapter; // let fragment have access
    private RecyclerView.LayoutManager layoutManager;

    private boolean recreate;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.autofill_recycler_view);
        recyclerView = (RecyclerView) findViewById(R.id.autofill_recycler);

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> allApps = getPackageManager().queryIntentActivities(intent, 0);
        final PackageManager pm = getPackageManager();
        Collections.sort(allApps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo lhs, ResolveInfo rhs) {
                return lhs.loadLabel(pm).toString().compareTo(rhs.loadLabel(pm).toString());
            }
        });
        recyclerAdapter = new AutofillRecyclerAdapter(new ArrayList<>(allApps), pm, this);
        recyclerView.setAdapter(recyclerAdapter);

        setTitle("Autofill Apps");

        recreate = false;
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            recreate = true;
            recyclerView.scrollToPosition(recyclerAdapter.getPosition(extras.getString("packageName")));
            showDialog(extras.getString("packageName"), extras.getString("appName"));
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
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
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
