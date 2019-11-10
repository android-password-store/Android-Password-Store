/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.widget.MultiselectableConstraintLayout
import java.io.File
import java.util.ArrayList
import java.util.TreeSet

abstract class EntryRecyclerAdapter internal constructor(val values: ArrayList<PasswordItem>) : RecyclerView.Adapter<EntryRecyclerAdapter.ViewHolder>() {
    internal val selectedItems: MutableSet<Int> = TreeSet()

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return values.size
    }

    fun clear() {
        this.values.clear()
        this.notifyDataSetChanged()
    }

    fun addAll(list: ArrayList<PasswordItem>) {
        this.values.addAll(list)
        this.notifyDataSetChanged()
    }

    fun add(item: PasswordItem) {
        this.values.add(item)
        this.notifyItemInserted(itemCount)
    }

    internal fun toggleSelection(position: Int) {
        if (!selectedItems.remove(position)) {
            selectedItems.add(position)
        }
    }

    // use this after an item is removed to update the positions of items in set
    // that followed the removed position
    fun updateSelectedItems(position: Int, selectedItems: MutableSet<Int>) {
        val temp = TreeSet<Int>()
        for (selected in selectedItems) {
            if (selected > position) {
                temp.add(selected - 1)
            } else {
                temp.add(selected)
            }
        }
        selectedItems.clear()
        selectedItems.addAll(temp)
    }

    fun remove(position: Int) {
        this.values.removeAt(position)
        this.notifyItemRemoved(position)

        // keep selectedItems updated so we know what to notifyItemChanged
        // (instead of just using notifyDataSetChanged)
        updateSelectedItems(position, selectedItems)
    }

    internal open fun getOnLongClickListener(holder: ViewHolder, pass: PasswordItem): View.OnLongClickListener {
        return View.OnLongClickListener { false }
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pass = values[position]
        holder.name.text = pass.toString()
        if (pass.type == PasswordItem.TYPE_CATEGORY) {
            holder.type.visibility = View.GONE
            holder.typeImage.setImageResource(R.drawable.ic_multiple_files_24dp)
            holder.folderIndicator.visibility = View.VISIBLE
            val childCount = (pass.file.list { current, name -> File(current, name).isFile } ?: emptyArray<File>()).size
            if (childCount > 0) {
                holder.childCount.visibility = View.VISIBLE
                holder.childCount.text = "$childCount"
            }
        } else {
            holder.typeImage.setImageResource(R.drawable.ic_action_secure_24dp)
            holder.name.text = pass.toString()
            holder.type.visibility = View.VISIBLE
            holder.type.text = pass.fullPathToParent.replace("(^/)|(/$)".toRegex(), "")
            holder.childCount.visibility = View.GONE
            holder.folderIndicator.visibility = View.GONE
        }

        holder.view.setOnClickListener(getOnClickListener(holder, pass))

        holder.view.setOnLongClickListener(getOnLongClickListener(holder, pass))

        // after removal, everything is rebound for some reason; views are shuffled?
        val selected = selectedItems.contains(position)
        holder.view.isSelected = selected
        (holder.itemView as MultiselectableConstraintLayout).setMultiSelected(selected)
    }

    protected abstract fun getOnClickListener(holder: ViewHolder, pass: PasswordItem): View.OnClickListener

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        // create a new view
        val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.password_row_layout, parent, false)
        return ViewHolder(v)
    }

    /*
     Provide a reference to the views for each data item
     Complex data items may need more than one view per item, and
     you provide access to all the views for a data item in a view holder
     each data item is just a string in this case
    */
    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val name: AppCompatTextView = view.findViewById(R.id.label)
        val type: AppCompatTextView = view.findViewById(R.id.type)
        val typeImage: AppCompatImageView = view.findViewById(R.id.type_image)
        val childCount: AppCompatTextView = view.findViewById(R.id.child_count)
        val folderIndicator: AppCompatImageView = view.findViewById(R.id.folder_indicator)
    }
}
