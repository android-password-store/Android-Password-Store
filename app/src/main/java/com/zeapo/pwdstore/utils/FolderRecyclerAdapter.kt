package com.zeapo.pwdstore.utils

import android.view.View

import com.zeapo.pwdstore.SelectFolderFragment

import java.util.ArrayList

class FolderRecyclerAdapter(
    private val listener: SelectFolderFragment.OnFragmentInteractionListener,
    values: ArrayList<PasswordItem>
) : EntryRecyclerAdapter(values) {

    override fun getOnClickListener(holder: ViewHolder, pass: PasswordItem): View.OnClickListener {
        return View.OnClickListener {
            listener.onFragmentInteraction(pass)
            notifyItemChanged(holder.adapterPosition)
        }
    }
}
