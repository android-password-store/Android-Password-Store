package com.zeapo.pwdstore.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

public abstract class EntryRecyclerAdapter extends RecyclerView.Adapter<EntryRecyclerAdapter.ViewHolder> {
    final Set<Integer> selectedItems = new TreeSet<>();
    private final Activity activity;
    private final ArrayList<PasswordItem> values;

    EntryRecyclerAdapter(Activity activity, ArrayList<PasswordItem> values) {
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

    void toggleSelection(int position) {
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
    View.OnLongClickListener getOnLongClickListener(ViewHolder holder, PasswordItem pass) {
        return v -> false;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        final PasswordItem pass = getValues().get(position);
        holder.name.setText(pass.toString());
        if (pass.getType() == PasswordItem.TYPE_CATEGORY) {
            holder.typeImage.setImageResource(R.drawable.ic_folder_grey600_24dp);
        } else {
            holder.typeImage.setImageResource(R.drawable.ic_action_secure);
            holder.name.setText(pass.toString());
        }

        holder.type.setText(pass.getFullPathToParent().replaceAll("(^/)|(/$)", ""));

        holder.view.setOnClickListener(getOnClickListener(holder, pass));

        holder.view.setOnLongClickListener(getOnLongClickListener(holder, pass));

        // after removal, everything is rebound for some reason; views are shuffled?
        boolean selected = selectedItems.contains(position);
        holder.view.setSelected(selected);
        if (selected) {
            holder.itemView.setBackgroundResource(R.color.deep_orange_200);
            holder.type.setTextColor(Color.BLACK);
        } else {
            holder.itemView.setBackgroundColor(Color.alpha(1));
            holder.type.setTextColor(ContextCompat.getColor(activity, R.color.grey_500));
        }
    }

    @NonNull
    protected abstract View.OnClickListener getOnClickListener(ViewHolder holder, PasswordItem pass);

    // Create new views (invoked by the layout manager)
    @Override
    @NonNull
    public PasswordRecyclerAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                                                                 int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.password_row_layout, parent, false);
        return new ViewHolder(v);
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public final View view;
        public final TextView name;
        final TextView type;
        final ImageView typeImage;

        ViewHolder(View v) {
            super(v);
            view = v;
            name = view.findViewById(R.id.label);
            type = view.findViewById(R.id.type);
            typeImage = view.findViewById(R.id.type_image);
        }
    }
}
