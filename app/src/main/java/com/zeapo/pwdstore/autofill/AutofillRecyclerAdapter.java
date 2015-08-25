package com.zeapo.pwdstore.autofill;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.support.v7.util.SortedList;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AutofillRecyclerAdapter extends RecyclerView.Adapter<AutofillRecyclerAdapter.ViewHolder> {
    private SortedList<ResolveInfo> apps;
    private ArrayList<ResolveInfo> allApps;
    private HashMap<String, Pair<Drawable, String>> iconMap;
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

    public AutofillRecyclerAdapter(List<ResolveInfo> allApps, HashMap<String, Pair<Drawable, String>> iconMap
            , final PackageManager pm, AutofillPreferenceActivity activity) {
         SortedList.Callback<ResolveInfo> callback = new SortedListAdapterCallback<ResolveInfo>(this) {
            @Override
            public int compare(ResolveInfo o1, ResolveInfo o2) {
                return o1.loadLabel(pm).toString().toLowerCase().compareTo(o2.loadLabel(pm).toString().toLowerCase());
            }

            @Override
            public boolean areContentsTheSame(ResolveInfo oldItem, ResolveInfo newItem) {
                return oldItem.loadLabel(pm).toString().equals(newItem.loadLabel(pm).toString());
            }

            @Override
            public boolean areItemsTheSame(ResolveInfo item1, ResolveInfo item2) {
                return item1.loadLabel(pm).toString().equals(item2.loadLabel(pm).toString());
            }
        };
        this.apps = new SortedList<>(ResolveInfo.class, callback);
        this.apps.addAll(allApps);
        this.allApps = new ArrayList<>(allApps);
        this.iconMap = new HashMap<>(iconMap);
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
        ResolveInfo app = apps.get(position);
        holder.packageName = app.activityInfo.packageName;

        holder.icon.setImageDrawable(iconMap.get(holder.packageName).first);
        holder.name.setText(iconMap.get(holder.packageName).second);

        holder.secondary.setVisibility(View.VISIBLE);
        holder.view.setBackgroundResource(R.color.grey_white_1000);

        SharedPreferences prefs
                = activity.getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
        String preference = prefs.getString(holder.packageName, "");
        switch (preference) {
            case "":
                holder.secondary.setVisibility(View.GONE);
                // "android:windowBackground"
                holder.view.setBackgroundResource(R.color.indigo_50);
                break;
            case "/first":
                holder.secondary.setText(R.string.autofill_apps_first);
                break;
            case "/never":
                holder.secondary.setText(R.string.autofill_apps_never);
                break;
            default:
                holder.secondary.setText("Match with " + preference);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    public int getPosition(String packageName) {
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).activityInfo.packageName.equals(packageName)) {
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
        for (ResolveInfo app : allApps) {
            if (app.loadLabel(pm).toString().toLowerCase().contains(s.toLowerCase())) {
                apps.add(app);
            } else {
                apps.remove(app);
            }
        }
        apps.endBatchedUpdates();
    }
}
