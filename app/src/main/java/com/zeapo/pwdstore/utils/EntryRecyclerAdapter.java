package com.zeapo.pwdstore.utils;

import android.app.Activity;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

public abstract class EntryRecyclerAdapter extends RecyclerView.Adapter<EntryRecyclerAdapter.ViewHolder> {
    private final Activity activity;
    protected final ArrayList<PasswordItem> values;
    protected final Set<Integer> selectedItems = new TreeSet<>();

    public EntryRecyclerAdapter(Activity activity, ArrayList<PasswordItem> values) {
        this.activity = activity;
        this.values = values;
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
        this.notifyItemInserted(getItemCount());
    }

    public void toggleSelection(int position) {
        if (!selectedItems.remove(position)) {
            selectedItems.add(position);
        }
    }

    // use this after an item is removed to update the positions of items in set
    // that followed the removed position
    public void updateSelectedItems(int position, Set<Integer> selectedItems) {
        Set<Integer> temp = new TreeSet<>();
        for (int selected : selectedItems) {
            if (selected > position) {
                temp.add(selected - 1);
            } else {
                temp.add(selected);
            }
        }
        selectedItems.clear();
        selectedItems.addAll(temp);
    }

    public void remove(int position) {
        this.values.remove(position);
        this.notifyItemRemoved(position);

        // keep selectedItems updated so we know what to notifyItemChanged
        // (instead of just using notifyDataSetChanged)
        updateSelectedItems(position, selectedItems);
    }

    @NonNull
    protected View.OnLongClickListener getOnLongClickListener(ViewHolder holder, PasswordItem pass) {
        return new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return false;
            }
        };
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final PasswordItem pass = getValues().get(position);
        holder.name.setText(pass.toString());
        if (pass.getType() == PasswordItem.TYPE_CATEGORY) {
            holder.typeImage.setImageResource(R.drawable.ic_folder_grey600_24dp);
            holder.name.setText(pass.toString() + "/");
        } else {
            holder.typeImage.setImageResource(R.drawable.ic_action_secure);
            holder.name.setText(pass.toString());
        }

        holder.type.setText(pass.getFullPathToParent());
        if (pass.getType() == PasswordItem.TYPE_CATEGORY) {
//            holder.card.setCardBackgroundColor(activity.getResources().getColor(R.color.blue_grey_200));
        } else {
//            holder.card.setCardBackgroundColor(activity.getResources().getColor(R.color.blue_grey_50));
        }

        holder.view.setOnClickListener(getOnClickListener(holder, pass));

        holder.view.setOnLongClickListener(getOnLongClickListener(holder, pass));

        // after removal, everything is rebound for some reason; views are shuffled?
        boolean selected = selectedItems.contains(position);
        holder.view.setSelected(selected);
        if (selected) {
            holder.itemView.setBackgroundResource(R.color.deep_orange_200);
            holder.type.setTextColor(Color.BLACK);
        } else {
            holder.itemView.setBackgroundResource(Color.alpha(1));
            holder.type.setTextColor(ContextCompat.getColor(activity, R.color.grey_500));
        }
    }

    @NonNull
    protected abstract View.OnClickListener getOnClickListener(ViewHolder holder, PasswordItem pass);

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public View view;
        public TextView name;
        public TextView type;
        public ImageView typeImage;

        public ViewHolder(View v) {
            super(v);
            view = v;
            name = (TextView) view.findViewById(R.id.label);
            type = (TextView) view.findViewById(R.id.type);
            typeImage = (ImageView) view.findViewById(R.id.type_image);
        }
    }

    // Create new views (invoked by the layout manager)
    @Override
    public PasswordRecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                                 int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.password_row_layout, parent, false);
        return new ViewHolder(v);
    }
}
