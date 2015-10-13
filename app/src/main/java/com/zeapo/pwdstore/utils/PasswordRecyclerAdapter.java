package com.zeapo.pwdstore.utils;

import android.support.v7.view.ActionMode;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.zeapo.pwdstore.PasswordFragment;
import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

public class PasswordRecyclerAdapter extends RecyclerView.Adapter<PasswordRecyclerAdapter.ViewHolder> {
    private final PasswordStore activity;
    private final ArrayList<PasswordItem> values;
    private final PasswordFragment.OnFragmentInteractionListener listener;
    private final Set<Integer> selectedItems;
    private ActionMode mActionMode;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public View view;
        public CardView card;
        public TextView name;
        public TextView type;

        public ViewHolder(View v) {
            super(v);
            view = v;
            card = (CardView) view.findViewById(R.id.password_card);
            name = (TextView) view.findViewById(R.id.label);
            type = (TextView) view.findViewById(R.id.type);
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public PasswordRecyclerAdapter(PasswordStore activity, PasswordFragment.OnFragmentInteractionListener listener, ArrayList<PasswordItem> values) {
        this.values = values;
        this.activity = activity;
        this.listener = listener;
        selectedItems = new TreeSet<>();
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

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        final PasswordItem pass = values.get(position);
        holder.name.setText(pass.toString());
        int sdk = android.os.Build.VERSION.SDK_INT;

        if (pass.getType() == PasswordItem.TYPE_CATEGORY) {
            holder.type.setText(pass.getFullPathName());
            holder.card.setCardBackgroundColor(activity.getResources().getColor(R.color.deep_orange_400));
        } else {
            holder.type.setText(pass.getFullPathName());
            holder.card.setCardBackgroundColor(activity.getResources().getColor(R.color.blue_grey_400));
        }

        holder.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mActionMode != null) {
                    toggleSelection(holder.getAdapterPosition(), holder.card, pass.getType());
                    if (selectedItems.isEmpty()) {
                        mActionMode.finish();
                    }
                } else {
                    listener.onFragmentInteraction(pass);
                }
            }
        });

        holder.view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mActionMode != null) {
                    return false;
                }
                toggleSelection(holder.getAdapterPosition(), holder.card, pass.getType());
                // Start the CAB using the ActionMode.Callback
                mActionMode = activity.startSupportActionMode(mActionModeCallback);
                return true;
            }
        });

        // after removal, everything is rebound for some reason; views are shuffled?
        holder.view.setSelected(selectedItems.contains(position));
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        // Called when the action mode is created; startActionMode() was called
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            mode.getMenuInflater().inflate(R.menu.context_pass, menu);
            return true;
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_delete_password:
                    activity.deletePasswords(PasswordRecyclerAdapter.this, new TreeSet<>(selectedItems));
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            for (Iterator it = selectedItems.iterator(); it.hasNext();) {
                // need the setSelected line in onBind
                notifyItemChanged((Integer) it.next());
                it.remove();
            }
            mActionMode = null;
        }
    };

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
        this.notifyItemInserted(values.size());
    }

    public void remove(int position) {
        this.values.remove(position);
        this.notifyItemRemoved(position);

        // keep selectedItems updated so we know what to notifyItemChanged
        // (instead of just using notifyDataSetChanged)
        updateSelectedItems(position, selectedItems);
    }

    public void toggleSelection(int position, CardView card, char type) {
        if (!selectedItems.remove(position)) {
            selectedItems.add(position);
            if (type == PasswordItem.TYPE_CATEGORY) {
                card.setCardBackgroundColor(activity.getResources().getColor(R.color.deep_orange_200));
            }
            else {
                card.setCardBackgroundColor(activity.getResources().getColor(R.color.blue_grey_200));
            }
        } else {
            if (type == PasswordItem.TYPE_CATEGORY) {
                card.setCardBackgroundColor(activity.getResources().getColor(R.color.deep_orange_400));
            }
            else {
                card.setCardBackgroundColor(activity.getResources().getColor(R.color.blue_grey_400));
            }
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
}
