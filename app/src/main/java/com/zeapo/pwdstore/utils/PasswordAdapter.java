package com.zeapo.pwdstore.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.DataSetObserver;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ExpandableListAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.crypto.PgpHandler;

import org.apache.commons.io.FileUtils;

import java.util.ArrayList;

public class PasswordAdapter extends ArrayAdapter<PasswordItem>  implements  ExpandableListAdapter{
    private final PasswordStore activity;
    private final ArrayList<PasswordItem> values;

    static class ViewHolder {
        public TextView name;
        public TextView type;
    }

    public PasswordAdapter(PasswordStore activity, ArrayList<PasswordItem> values) {
        super(activity, R.layout.password_row_layout, values);
        this.values = values;
        this.activity = activity;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver dataSetObserver) {

    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {

    }

    @Override
    public int getGroupCount() {
        return values.size();
    }

    @Override
    public int getChildrenCount(int i) {
        if (values.get(i).getType() == PasswordItem.TYPE_CATEGORY)
            return 0;
        else
            return 1;
    }

    @Override
    public Object getGroup(int i) {
        return values.get(i);
    }

    @Override
    public Object getChild(int i, int i2) {
        return null;
    }

    @Override
    public long getGroupId(int i) {
        return 0;
    }

    @Override
    public long getChildId(int i, int i2) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int i, boolean b, View convertView, ViewGroup viewGroup) {
        View rowView = convertView;
        PasswordItem pass = values.get(i);

        // reuse for performance, holder pattern!
        if (rowView == null) {
            LayoutInflater inflater = (LayoutInflater) activity
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            rowView = inflater.inflate(R.layout.password_row_layout, null);

            ViewHolder viewHolder = new ViewHolder();
            viewHolder.name = (TextView) rowView.findViewById(R.id.label);
            viewHolder.type = (TextView) rowView.findViewById(R.id.type);
            rowView.setTag(viewHolder);
        }

        ViewHolder holder = (ViewHolder) rowView.getTag();

        holder.name.setText(pass.toString());

        if (pass.getType() == PasswordItem.TYPE_CATEGORY) {
            holder.name.setTextColor(this.activity.getResources().getColor(android.R.color.holo_blue_dark));
            holder.name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            holder.type.setText("Category: ");
        } else {
            holder.type.setText("Password: ");
            holder.name.setTextColor(this.activity.getResources().getColor(android.R.color.holo_orange_dark));
            holder.name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        }

        return rowView;
    }

    @Override
    public View getChildView(int i, int i2, boolean b, View view, ViewGroup viewGroup) {
        final PasswordItem pass = values.get(i);

        LayoutInflater inflater = (LayoutInflater) this.activity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        view = inflater.inflate(R.layout.child_row_layout, null);

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.crypto_show_button:
                        activity.decryptPassword(pass);
                        break;
                    case R.id.crypto_delete_button:
                        activity.deletePassword(pass);
                        break;
                }
            }
        };

        ((ImageButton) view.findViewById(R.id.crypto_show_button)).setOnClickListener(onClickListener);
        ((ImageButton) view.findViewById(R.id.crypto_delete_button)).setOnClickListener(onClickListener);

        return view;
    }

    @Override
    public boolean isChildSelectable(int i, int i2) {
        return false;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public void onGroupExpanded(int i) {

    }

    @Override
    public void onGroupCollapsed(int i) {

    }

    @Override
    public long getCombinedChildId(long l, long l2) {
        return 0;
    }

    @Override
    public long getCombinedGroupId(long l) {
        return 0;
    }
}