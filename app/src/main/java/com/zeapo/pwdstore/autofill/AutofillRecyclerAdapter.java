package com.zeapo.pwdstore.autofill;

import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;

public class AutofillRecyclerAdapter extends RecyclerView.Adapter<AutofillRecyclerAdapter.ViewHolder> {
    private ArrayList<ApplicationInfo> apps;
    private PackageManager pm;
    private AutofillActivity activity;

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
            DialogFragment df = new AutofillFragment();
            Bundle args = new Bundle();
            args.putString("packageName", packageName);
            args.putString("appName", name.getText().toString());
            args.putInt("position", getAdapterPosition());
            df.setArguments(args);
            df.show(activity.getFragmentManager(), "autofill_dialog");
        }
    }

    public AutofillRecyclerAdapter(ArrayList<ApplicationInfo> apps, PackageManager pm, AutofillActivity activity) {
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
        ApplicationInfo app = apps.get(position);
        holder.name.setText(pm.getApplicationLabel(app));
        SharedPreferences prefs
                = activity.getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
        String preference = prefs.getString(app.packageName, "first");
        switch (preference) {
            case "first":
                holder.secondary.setText("Automatically match with password");
                break;
            case "never":
                holder.secondary.setText("Never autofill");
                break;
            default:
                holder.secondary.setText("Match with " + preference);
                break;
        }
        holder.icon.setImageDrawable(pm.getApplicationIcon(app));
        holder.packageName = app.packageName;
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    public boolean contains(String packageName) {
        for (ApplicationInfo app : apps) {
            if (app.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    public void add(String packageName) {
        try {
            ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
            this.apps.add(app);
            notifyItemInserted(apps.size());
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }
}
