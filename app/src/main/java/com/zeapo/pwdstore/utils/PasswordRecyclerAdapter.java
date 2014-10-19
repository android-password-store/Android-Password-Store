package com.zeapo.pwdstore.utils;

import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.zeapo.pwdstore.PasswordFragment;
import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;

import java.util.ArrayList;

public class PasswordRecyclerAdapter extends RecyclerView.Adapter<PasswordRecyclerAdapter.ViewHolder> {
    private final PasswordStore activity;
    private final ArrayList<PasswordItem> values;
    private final PasswordFragment.OnFragmentInteractionListener listener;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public View view;
        public TextView name;
        public TextView type;
        public int position;

        public ViewHolder(View v) {
            super(v);
            view = v;
            name = (TextView) view.findViewById(R.id.label);
            type = (TextView) view.findViewById(R.id.type);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public PasswordRecyclerAdapter(PasswordStore activity, PasswordFragment.OnFragmentInteractionListener listener, ArrayList<PasswordItem> values) {
        this.values = values;
        this.activity = activity;
        this.listener = listener;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public PasswordRecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                   int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.password_row_layout, parent, false);
        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        final PasswordItem pass = values.get(position);
        holder.name.setText(pass.toString());
        int sdk = android.os.Build.VERSION.SDK_INT;

        if (pass.getType() == PasswordItem.TYPE_CATEGORY) {
            holder.type.setText("C");
            if(sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                holder.type.setBackgroundDrawable(activity.getResources().getDrawable(R.drawable.category_rectangle));
            } else {
                holder.type.setBackground(activity.getResources().getDrawable(R.drawable.category_rectangle));
            }

            holder.type.setTextColor(activity.getResources().getColor(R.color.deep_orange_50));
        } else {
            holder.type.setText("P");
            if(sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                holder.type.setBackgroundDrawable(activity.getResources().getDrawable(R.drawable.password_rectangle));
            } else {
                holder.type.setBackground(activity.getResources().getDrawable(R.drawable.password_rectangle));
            }

            holder.type.setTextColor(activity.getResources().getColor(R.color.blue_grey_50));
        }

        holder.position = position;

        holder.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onFragmentInteraction(pass);
            }
        });
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return values.size();
    }

    public ArrayList<PasswordItem> getValues() {
        return this.values;
    }

    public void clear() {
        this.values.clear();
        this.notifyDataSetChanged();
    }

    public void addAll(ArrayList<PasswordItem> list) {
        this.values.addAll(list);
        this.notifyDataSetChanged();
    }

    public void add(PasswordItem item) {
        this.values.add(item);
        this.notifyDataSetChanged();
    }

}