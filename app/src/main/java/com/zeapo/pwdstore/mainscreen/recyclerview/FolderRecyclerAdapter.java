package com.zeapo.pwdstore.mainscreen.recyclerview;

import android.support.annotation.NonNull;
import android.view.View;

import com.zeapo.pwdstore.mainscreen.SelectFolderActivity;
import com.zeapo.pwdstore.mainscreen.fragments.SelectFolderFragment;
import com.zeapo.pwdstore.utils.PasswordItem;

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
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onFragmentInteraction(pass);
                notifyItemChanged(holder.getAdapterPosition());
            }
        };
    }

}
