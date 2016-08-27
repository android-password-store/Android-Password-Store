package com.zeapo.pwdstore.utils;

import android.support.annotation.NonNull;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.zeapo.pwdstore.PasswordFragment;
import com.zeapo.pwdstore.PasswordStore;
import com.zeapo.pwdstore.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

public class PasswordRecyclerAdapter extends EntryRecyclerAdapter {
    private final PasswordStore activity;
    private final PasswordFragment.OnFragmentInteractionListener listener;
    private ActionMode mActionMode;
    private Boolean canEdit;

    // Provide a suitable constructor (depends on the kind of dataset)
    public PasswordRecyclerAdapter(PasswordStore activity, PasswordFragment.OnFragmentInteractionListener listener, ArrayList<PasswordItem> values) {
        super(activity, values);
        this.activity = activity;
        this.listener = listener;
    }

    @Override
    @NonNull
    protected View.OnLongClickListener getOnLongClickListener(final ViewHolder holder, final PasswordItem pass) {
        return new View.OnLongClickListener() {
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
        };
    }

    @Override
    @NonNull
    protected View.OnClickListener getOnClickListener(final ViewHolder holder, final PasswordItem pass) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mActionMode != null) {
                    toggleSelection(holder.getAdapterPosition());
                    mActionMode.setTitle("" + selectedItems.size());
                    if (selectedItems.isEmpty()) {
                        mActionMode.finish();
                    } else if (selectedItems.size() == 1 && !canEdit) {
                        if (getValues().get(selectedItems.iterator().next()).getType() == PasswordItem.TYPE_PASSWORD) {
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
        };
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
                    activity.editPassword(getValues().get(selectedItems.iterator().next()));
                    mode.finish();
                    return true;
                case R.id.menu_move_password:
                    ArrayList<PasswordItem> selectedPasswords = new ArrayList<>();
                    for (Integer id : selectedItems) {
                        selectedPasswords.add(getValues().get(id));
                    }
                    activity.movePasswords(selectedPasswords);
                default:
                    return false;
            }
        }

        // Called when the user exits the action mode
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            for (Iterator<Integer> it = selectedItems.iterator(); it.hasNext(); ) {
                // need the setSelected line in onBind
                notifyItemChanged(it.next());
                it.remove();
            }
            mActionMode = null;
        }
    };
}
