package com.zeapo.pwdstore.autofill;

import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class AutofillPreferenceActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    AutofillRecyclerAdapter recyclerAdapter; // let fragment have access
    private RecyclerView.LayoutManager layoutManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // otherwise if called from settings
        setContentView(R.layout.autofill_recycler_view);
        recyclerView = (RecyclerView) findViewById(R.id.autofill_recycler);

        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
        
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
        Collections.sort(apps, new Comparator<ApplicationInfo>() {
            @Override
            public int compare(ApplicationInfo lhs, ApplicationInfo rhs) {
                return lhs.loadLabel(pm).toString().compareTo(rhs.loadLabel(pm).toString());
            }
        });
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
                // TODO do this async probably. it lags.
                MatrixCursor matrixCursor = new MatrixCursor(new String[]{"_id", "package", "label"});
                for (ApplicationInfo applicationInfo : allApps) {
                    if (applicationInfo.loadLabel(pm).toString().toLowerCase().contains(newText.toLowerCase())) {
                        matrixCursor.addRow(new Object[]{0, applicationInfo.packageName, applicationInfo.loadLabel(pm)});
                    }
                }
                SimpleCursorAdapter simpleCursorAdapter = new SimpleCursorAdapter(AutofillPreferenceActivity.this
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
                showDialog(packageName, appName);
                return true;
            }
        });

        setTitle("Autofill Apps");

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            showDialog(extras.getString("packageName"), extras.getString("appName"));
        }
    }

    public void showDialog(String packageName, String appName) {
        DialogFragment df = new AutofillFragment();
        Bundle args = new Bundle();
        args.putString("packageName", packageName);
        args.putString("appName", appName);
        args.putInt("position", recyclerAdapter.getPosition(packageName));
        df.setArguments(args);
        df.show(getFragmentManager(), "autofill_dialog");
        // TODO if called from dialog 'Settings' button, should activity finish at OK?
    }
}
