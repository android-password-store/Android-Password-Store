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
    private ArrayList<AppInfo> allApps;
    private PackageManager pm;
    private AutofillPreferenceActivity activity;

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        public View view;
        public TextView name;
        public TextView secondary;
        public ImageView icon;
        public String packageName;

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
            activity.showDialog(packageName, name.getText().toString());
        }

    }

    public static class AppInfo {
        public String label;
        public String packageName;
        public Drawable icon;

        public AppInfo(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    public AutofillRecyclerAdapter(List<AppInfo> allApps, final PackageManager pm
            , AutofillPreferenceActivity activity) {
         SortedList.Callback<AppInfo> callback = new SortedListAdapterCallback<AppInfo>(this) {
             @Override
             public int compare(AppInfo o1, AppInfo o2) {
                 return o1.label.toLowerCase().compareTo(o2.label.toLowerCase());
             }

             @Override
             public boolean areContentsTheSame(AppInfo oldItem, AppInfo newItem) {
                 return oldItem.label.equals(newItem.label);
             }

             @Override
             public boolean areItemsTheSame(AppInfo item1, AppInfo item2) {
                 return item1.packageName.equals(item2.packageName);
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

        holder.icon.setImageDrawable(app.icon);
        holder.name.setText(app.label);

        holder.secondary.setVisibility(View.VISIBLE);
        holder.view.setBackgroundResource(R.color.grey_white_1000);

        SharedPreferences prefs
                = activity.getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
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
                holder.secondary.setText("Match with " + preference.split("\n")[0]);
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

    public int getPosition(String packageName) {
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).packageName.equals(packageName)) {
                return i;
            }
        }
        return -1;
    }

    public void filter(String s) {
        if (s.isEmpty()) {
            apps.addAll(allApps);
            return;
        }
        apps.beginBatchedUpdates();
        for (AppInfo app : allApps) {
            if (app.label.toLowerCase().contains(s.toLowerCase())) {
                apps.add(app);
            } else {
                apps.remove(app);
            }
        }
        apps.endBatchedUpdates();
    }
}
