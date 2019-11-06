/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.adapters

import android.view.View
import com.zeapo.pwdstore.SelectFolderFragment
import com.zeapo.pwdstore.utils.PasswordItem
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
