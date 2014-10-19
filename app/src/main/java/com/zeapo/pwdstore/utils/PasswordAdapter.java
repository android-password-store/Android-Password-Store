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
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ExpandableListAdapter;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;
import com.zeapo.pwdstore.crypto.PgpHandler;

import org.apache.commons.io.FileUtils;

import java.util.ArrayList;

public class PasswordAdapter extends ArrayAdapter<PasswordItem>{
    private final PasswordStore activity;
    private final ArrayList<PasswordItem> values;

    static class ViewHolder {
        public TextView name;
        public TextView type;
        public TextView back_name;
    }

    public PasswordAdapter(PasswordStore activity, ArrayList<PasswordItem> values) {
        super(activity, R.layout.password_row_layout, values);
        this.values = values;
        this.activity = activity;
    }

    public ArrayList<PasswordItem> getValues()  {
        return values;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        View rowView = convertView;
        final PasswordItem pass = values.get(i);

        // reuse for performance, holder pattern!
        if (rowView == null) {
            LayoutInflater inflater = (LayoutInflater) activity
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            rowView = inflater.inflate(R.layout.password_row_layout, viewGroup, false);

            ViewHolder viewHolder = new ViewHolder();
            viewHolder.name = (TextView) rowView.findViewById(R.id.label);
            viewHolder.back_name = (TextView) rowView.findViewById(R.id.label_back);
            viewHolder.type = (TextView) rowView.findViewById(R.id.type);
            rowView.setTag(viewHolder);
        }

        ViewHolder holder = (ViewHolder) rowView.getTag();

        holder.name.setText(pass.toString());
        holder.back_name.setText(pass.toString());

        if (pass.getType() == PasswordItem.TYPE_CATEGORY) {
            holder.name.setTextColor(this.activity.getResources().getColor(android.R.color.holo_blue_dark));
            holder.name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            holder.type.setText("Category: ");
        } else {
            holder.type.setText("Password: ");
            holder.name.setTextColor(this.activity.getResources().getColor(android.R.color.holo_orange_dark));
            holder.name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

            holder.back_name.setTextColor(this.activity.getResources().getColor(android.R.color.white));
            holder.back_name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC));


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

            ((ImageButton) rowView.findViewById(R.id.crypto_show_button)).setOnClickListener(onClickListener);
            ((ImageButton) rowView.findViewById(R.id.crypto_delete_button)).setOnClickListener(onClickListener);
        }

        return rowView;
    }
}