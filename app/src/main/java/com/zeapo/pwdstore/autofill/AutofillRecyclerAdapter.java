package com.zeapo.pwdstore.autofill;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class AutofillRecyclerAdapter extends RecyclerView.Adapter<AutofillRecyclerAdapter.ViewHolder> {
    private ArrayList<ApplicationInfo> apps;
    private PackageManager pm;
    private AutofillPreferenceActivity activity;
    private final Set<Integer> selectedItems;
    private ActionMode actionMode;

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
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
            view.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (actionMode != null) {
                toggleSelection(getAdapterPosition(), view);
                if (selectedItems.isEmpty()) {
                    actionMode.finish();
                }
            } else {
                activity.showDialog(packageName, name.getText().toString());
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (actionMode != null) {
                return false;
            }
            toggleSelection(getAdapterPosition(), view);
            // Start the CAB using the ActionMode.Callback
            actionMode = activity.startSupportActionMode(actionModeCallback);
            return true;
        }
    }

    public AutofillRecyclerAdapter(ArrayList<ApplicationInfo> apps, PackageManager pm, AutofillPreferenceActivity activity) {
        this.apps = apps;
        this.pm = pm;
        this.activity = activity;
        this.selectedItems = new TreeSet<>(Collections.reverseOrder());
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

        // it shouldn't be possible for prefs.getString to not find the app...use defValue anyway
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
        String defValue = settings.getBoolean("autofill_default", true) ? "first" : "never";
        SharedPreferences prefs
                = activity.getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
        String preference = prefs.getString(app.packageName, defValue);
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

        holder.view.setSelected(selectedItems.contains(position));
    }

    @Override
    public int getItemCount() {
        return apps.size();
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

    public int getPosition(String packageName) {
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).packageName.equals(packageName)) {
                return i;
            }
        }
        return -1;
    }

    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.context_pass, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_delete_password:
                    // don't ask for confirmation
                    for (int position : selectedItems) {
                        remove(position);
                    }
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            for (Iterator it = selectedItems.iterator(); it.hasNext();) {
                // need the setSelected line in onBind
                notifyItemChanged((Integer) it.next());
                it.remove();
            }
            actionMode = null;
        }
    };

    public void remove(int position) {
        ApplicationInfo applicationInfo = this.apps.get(position);
        SharedPreferences prefs
                = activity.getApplicationContext().getSharedPreferences("autofill", Context.MODE_PRIVATE);
        prefs.edit().remove(applicationInfo.packageName).apply();

        this.apps.remove(position);
        this.notifyItemRemoved(position);
    }

    public void toggleSelection(int position, View view) {
        if (!selectedItems.remove(position)) {
            selectedItems.add(position);
            view.setSelected(true);
        } else {
            view.setSelected(false);
        }
    }
}
