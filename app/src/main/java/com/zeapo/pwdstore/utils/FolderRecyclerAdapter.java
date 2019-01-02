package com.zeapo.pwdstore.utils;

import androidx.annotation.NonNull;
import android.view.View;

import com.zeapo.pwdstore.SelectFolderActivity;
import com.zeapo.pwdstore.SelectFolderFragment;

import java.util.ArrayList;

public class FolderRecyclerAdapter extends EntryRecyclerAdapter {
    private final SelectFolderFragment.OnFragmentInteractionListener listener;

    // Provide a suitable constructor (depends on the kind of dataset)
    public FolderRecyclerAdapter(SelectFolderActivity activity, SelectFolderFragment.OnFragmentInteractionListener listener, ArrayList<PasswordItem> values) {
        super(activity, values);
        this.listener = listener;
    }

    @NonNull
    protected View.OnClickListener getOnClickListener(final ViewHolder holder, final PasswordItem pass) {
        return v -> {
            listener.onFragmentInteraction(pass);
            notifyItemChanged(holder.getAdapterPosition());
        };
    }

}
