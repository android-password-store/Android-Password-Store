package com.zeapo.pwdstore.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;

public class PasswordAdapter extends ArrayAdapter<PasswordItem> {
    private final Context context;
    private final ArrayList<PasswordItem> values;

    static class ViewHolder {
        public TextView name;
        public TextView type;
    }

    public PasswordAdapter(Context context, ArrayList<PasswordItem> values) {
        super(context, R.layout.password_row_layout, values);
        this.context = context;
        this.values = values;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View rowView = convertView;
        PasswordItem pass = values.get(position);

        // reuse for performance, holder pattern!
        if (rowView == null) {
            LayoutInflater inflater = (LayoutInflater) context
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
            holder.name.setTextColor(this.context.getResources().getColor(android.R.color.holo_blue_dark));
            holder.name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            holder.type.setText("Category: ");
        } else {
            holder.type.setText("Password: ");
            holder.name.setTextColor(this.context.getResources().getColor(android.R.color.holo_orange_dark));
            holder.name.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        }

        return rowView;
    }
}