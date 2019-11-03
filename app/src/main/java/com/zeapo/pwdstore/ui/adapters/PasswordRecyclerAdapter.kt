/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.adapters

import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode

import com.zeapo.pwdstore.PasswordFragment
import com.zeapo.pwdstore.PasswordStore
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.PasswordItem

import java.util.ArrayList
import java.util.TreeSet

class PasswordRecyclerAdapter(
    private val activity: PasswordStore,
    private val listener: PasswordFragment.OnFragmentInteractionListener,
    values: ArrayList<PasswordItem>
) : EntryRecyclerAdapter(values) {
    var actionMode: ActionMode? = null
    private var canEdit: Boolean = false
    private val actionModeCallback = object : ActionMode.Callback {

        // Called when the action mode is created; startActionMode() was called
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            // Inflate a menu resource providing context menu items
            mode.menuInflater.inflate(R.menu.context_pass, menu)
            // hide the fab
            activity.findViewById<View>(R.id.fab).visibility = View.GONE
            return true
        }

        // Called each time the action mode is shown. Always called after onCreateActionMode, but
        // may be called multiple times if the mode is invalidated.
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.findItem(R.id.menu_edit_password).isVisible = canEdit
            return true // Return false if nothing is done
        }

        // Called when the user selects a contextual menu item
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.menu_delete_password -> {
                    activity.deletePasswords(this@PasswordRecyclerAdapter, TreeSet(selectedItems))
                    mode.finish() // Action picked, so close the CAB
                    return true
                }
                R.id.menu_edit_password -> {
                    activity.editPassword(values[selectedItems.iterator().next()])
                    mode.finish()
                    return true
                }
                R.id.menu_move_password -> {
                    val selectedPasswords = ArrayList<PasswordItem>()
                    for (id in selectedItems) {
                        selectedPasswords.add(values[id])
                    }
                    activity.movePasswords(selectedPasswords)
                    return false
                }
                else -> return false
            }
        }

        // Called when the user exits the action mode
        override fun onDestroyActionMode(mode: ActionMode) {
            val it = selectedItems.iterator()
            while (it.hasNext()) {
                // need the setSelected line in onBind
                notifyItemChanged(it.next())
                it.remove()
            }
            actionMode = null
            // show the fab
            activity.findViewById<View>(R.id.fab).visibility = View.VISIBLE
        }
    }

    override fun getOnLongClickListener(holder: ViewHolder, pass: PasswordItem): View.OnLongClickListener {
        return View.OnLongClickListener {
            if (actionMode != null) {
                return@OnLongClickListener false
            }
            toggleSelection(holder.adapterPosition)
            canEdit = pass.type == PasswordItem.TYPE_PASSWORD
            // Start the CAB using the ActionMode.Callback
            actionMode = activity.startSupportActionMode(actionModeCallback)
            actionMode?.title = "" + selectedItems.size
            actionMode?.invalidate()
            notifyItemChanged(holder.adapterPosition)
            true
        }
    }

    override fun getOnClickListener(holder: ViewHolder, pass: PasswordItem): View.OnClickListener {
        return View.OnClickListener {
            if (actionMode != null) {
                toggleSelection(holder.adapterPosition)
                actionMode?.title = "" + selectedItems.size
                if (selectedItems.isEmpty()) {
                    actionMode?.finish()
                } else if (selectedItems.size == 1 && (canEdit.not())) {
                    if (values[selectedItems.iterator().next()].type == PasswordItem.TYPE_PASSWORD) {
                        canEdit = true
                        actionMode?.invalidate()
                    }
                } else if (selectedItems.size >= 1 && canEdit) {
                    canEdit = false
                    actionMode?.invalidate()
                }
            } else {
                listener.onFragmentInteraction(pass)
            }
            notifyItemChanged(holder.adapterPosition)
        }
    }
}
