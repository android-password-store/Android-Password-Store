/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.adapters

import com.zeapo.pwdstore.SelectFolderFragment
import com.zeapo.pwdstore.utils.PasswordItem

class FolderRecyclerAdapter(
    private val listener: SelectFolderFragment.OnFragmentInteractionListener
) : EntryRecyclerAdapter() {

    override fun onItemClicked(holder: PasswordItemViewHolder, item: PasswordItem) {
        listener.onFragmentInteraction(item)
        notifyItemChanged(holder.absoluteAdapterPosition)
    }
}
