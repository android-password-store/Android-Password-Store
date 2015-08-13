package com.zeapo.pwdstore.autofill;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;

public class AutofillRecyclerAdapter extends RecyclerView.Adapter<AutofillRecyclerAdapter.ViewHolder> {
    private ArrayList<ResolveInfo> apps;
    private PackageManager pm;
    private AutofillPreferenceActivity activity;
    private ActionMode actionMode;

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

    public AutofillRecyclerAdapter(ArrayList<ResolveInfo> apps, PackageManager pm, AutofillPreferenceActivity activity) {
        this.apps = apps;
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
        holder.name.setText(app.loadLabel(pm));
        holder.icon.setImageDrawable(app.loadIcon(pm));
        holder.packageName = app.activityInfo.packageName;

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
            case "first":
                holder.secondary.setText("Automatically match");
                break;
            case "never":
                holder.secondary.setText("Never match");
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

}
