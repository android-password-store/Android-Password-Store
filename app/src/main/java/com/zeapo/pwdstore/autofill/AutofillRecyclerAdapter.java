package com.zeapo.pwdstore.autofill;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.List;

public class AutofillRecyclerAdapter extends RecyclerView.Adapter<AutofillRecyclerAdapter.ViewHolder> {

    private SortedList<AppInfo> apps;
    private ArrayList<AppInfo> allApps; // for filtering, maintain a list of all
    private PackageManager pm;
    private AutofillPreferenceActivity activity;

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public View view;
        public TextView name;
        public TextView secondary;
        public ImageView icon;
        public String packageName;
        public String appName;

        public ViewHolder(View view) {
            super(view);
            this.view = view;
            name = (TextView) view.findViewById(R.id.app_name);
            secondary = (TextView) view.findViewById(R.id.secondary_text);
            icon = (ImageView) view.findViewById(R.id.app_icon);
            view.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            activity.showDialog(packageName, appName);
        }

    }

    public static class AppInfo {
        public String packageName;
        public String appName;
        public Drawable icon;

        public AppInfo(String packageName, String appName, Drawable icon) {
            this.packageName = packageName;
            this.appName = appName;
            this.icon = icon;
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o instanceof AppInfo && this.appName.equals(((AppInfo) o).appName);
        }
    }

    public AutofillRecyclerAdapter(List<AppInfo> allApps, final PackageManager pm
            , AutofillPreferenceActivity activity) {
        SortedList.Callback<AppInfo> callback = new SortedListAdapterCallback<AppInfo>(this) {
            // don't take into account secondary text. This is good enough
            // for the limited add/remove usage for websites
            @Override
            public int compare(AppInfo o1, AppInfo o2) {
             return o1.appName.toLowerCase().compareTo(o2.appName.toLowerCase());
            }

            @Override
            public boolean areContentsTheSame(AppInfo oldItem, AppInfo newItem) {
             return oldItem.appName.equals(newItem.appName);
            }

            @Override
            public boolean areItemsTheSame(AppInfo item1, AppInfo item2) {
             return item1.appName.equals(item2.appName);
            }
        };
        this.apps = new SortedList<>(AppInfo.class, callback);
        this.apps.addAll(allApps);
        this.allApps = new ArrayList<>(allApps);
        this.pm = pm;
        this.activity = activity;
    }

    @Override
    public AutofillRecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.autofill_row_layout, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(AutofillRecyclerAdapter.ViewHolder holder, int position) {
        AppInfo app = apps.get(position);
        holder.packageName = app.packageName;
        holder.appName = app.appName;

        holder.icon.setImageDrawable(app.icon);
        holder.name.setText(app.appName);

        holder.secondary.setVisibility(View.VISIBLE);
        holder.view.setBackgroundResource(R.color.grey_white_1000);

        SharedPreferences prefs;
        if (!app.appName.equals(app.packageName)) {
            prefs = activity.getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
        } else {
            prefs = activity.getApplicationContext().getSharedPreferences("autofill_web", Context.MODE_PRIVATE);
        }
        String preference = prefs.getString(holder.packageName, "");
        switch (preference) {
            case "":
                holder.secondary.setVisibility(View.GONE);
                holder.view.setBackgroundResource(0);
                break;
            case "/first":
                holder.secondary.setText(R.string.autofill_apps_first);
                break;
            case "/never":
                holder.secondary.setText(R.string.autofill_apps_never);
                break;
            default:
                holder.secondary.setText(R.string.autofill_apps_match);
                holder.secondary.append(" " + preference.split("\n")[0]);
                if ((preference.trim().split("\n").length - 1) > 0) {
                    holder.secondary.append(" and "
                            + (preference.trim().split("\n").length - 1) + " more");
                }
                break;
        }
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    public int getPosition(String appName) {
        return apps.indexOf(new AppInfo(null, appName, null));
    }

    public void addWebsite(String appName) {
        Drawable icon = null;
        try {
            icon = activity.getPackageManager().getApplicationIcon("com.android.browser");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        apps.add(new AppInfo(appName, appName, icon));
        allApps.add(new AppInfo(appName, appName, icon));
    }

    public void removeWebsite(String appName) {
        apps.remove(new AppInfo(null, appName, null));
        allApps.remove(new AppInfo(null, appName, null)); // compare with equals
    }

    public void filter(String s) {
        if (s.isEmpty()) {
            apps.addAll(allApps);
            return;
        }
        apps.beginBatchedUpdates();
        for (AppInfo app : allApps) {
            if (app.appName.toLowerCase().contains(s.toLowerCase())) {
                apps.add(app);
            } else {
                apps.remove(app);
            }
        }
        apps.endBatchedUpdates();
    }
}
