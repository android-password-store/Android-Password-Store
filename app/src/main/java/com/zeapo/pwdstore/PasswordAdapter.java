package com.zeapo.pwdstore;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;

public class PasswordAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final ArrayList<String> values;

    static class ViewHolder {
        public TextView text;
    }

    public PasswordAdapter(Context context, ArrayList<String> values) {
        super(context, R.layout.password_row_layout, values);
        this.context = context;
        this.values = values;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View rowView = convertView;

        // reuse for performance, holder pattern!
        if (rowView == null) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            rowView = inflater.inflate(R.layout.password_row_layout, null);

            ViewHolder viewHolder = new ViewHolder();
            viewHolder.text = (TextView) rowView.findViewById(R.id.label);
            rowView.setTag(viewHolder);
        }

        ViewHolder holder = (ViewHolder) rowView.getTag();
        holder.text.setText( values.get(position) );

        return rowView;
    }
}