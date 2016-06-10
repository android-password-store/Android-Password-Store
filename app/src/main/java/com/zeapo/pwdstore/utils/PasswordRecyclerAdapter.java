package com.zeapo.pwdstore.utils;

import android.graphics.Color;
import android.os.Build;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
    private Boolean canEdit;

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
        if (pass.getType() == PasswordItem.TYPE_CATEGORY) {
            holder.typeImage.setImageResource(R.drawable.ic_folder_grey600_24dp);
            holder.name.setText(pass.toString() + "/");
        } else {
            holder.typeImage.setImageResource(R.drawable.ic_action_secure);
            holder.name.setText(pass.toString());
        }
        int sdk = Build.VERSION.SDK_INT;

        holder.type.setText(pass.getFullPathName());
        if (pass.getType() == PasswordItem.TYPE_CATEGORY) {
//            holder.card.setCardBackgroundColor(activity.getResources().getColor(R.color.blue_grey_200));
        } else {
//            holder.card.setCardBackgroundColor(activity.getResources().getColor(R.color.blue_grey_50));
        }

        holder.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mActionMode != null) {
                    toggleSelection(holder.getAdapterPosition());
                    mActionMode.setTitle("" + selectedItems.size());
                    if (selectedItems.isEmpty()) {
                        mActionMode.finish();
                    } else if (selectedItems.size() == 1 && !canEdit) {
                        if (values.get(selectedItems.iterator().next()).getType() == PasswordItem.TYPE_PASSWORD) {
                            canEdit = true;
                            mActionMode.invalidate();
                        }
                    } else if (selectedItems.size() >= 1 && canEdit) {
                        canEdit = false;
                        mActionMode.invalidate();
                    }
                } else {
                    listener.onFragmentInteraction(pass);
                }
                notifyItemChanged(holder.getAdapterPosition());
            }
        });

        holder.view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mActionMode != null) {
                    return false;
                }
                toggleSelection(holder.getAdapterPosition());
                canEdit = pass.getType() == PasswordItem.TYPE_PASSWORD;
                // Start the CAB using the ActionMode.Callback
                mActionMode = activity.startSupportActionMode(mActionModeCallback);
                mActionMode.setTitle("" + selectedItems.size());
                mActionMode.invalidate();
                notifyItemChanged(holder.getAdapterPosition());
                return true;
            }
        });

        // after removal, everything is rebound for some reason; views are shuffled?
        boolean selected = selectedItems.contains(position);
        holder.view.setSelected(selected);
        if (selected) {
            holder.itemView.setBackgroundResource(R.color.orange_200);
            holder.type.setTextColor(Color.BLACK);
        } else {
            holder.itemView.setBackgroundResource(Color.alpha(1));
            holder.type.setTextColor(activity.getColor(R.color.grey_500));
        }
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
            if (canEdit) {
                menu.findItem(R.id.menu_edit_password).setVisible(true);
            } else {
                menu.findItem(R.id.menu_edit_password).setVisible(false);
            }
            return true; // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_delete_password:
                    activity.deletePasswords(PasswordRecyclerAdapter.this, new TreeSet<>(selectedItems));
                    mode.finish(); // Action picked, so close the CAB
                    return true;
                case R.id.menu_edit_password:
                    activity.editPassword(values.get(selectedItems.iterator().next()));
                    mode.finish();
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
}
