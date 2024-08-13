/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.adapters

import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.widget.RecyclerView
import app.passwordstore.R
import app.passwordstore.data.password.PasswordItem
import app.passwordstore.util.coroutines.DispatcherProvider
import app.passwordstore.util.viewmodel.SearchableRepositoryAdapter
import app.passwordstore.util.viewmodel.stableId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

open class PasswordItemRecyclerAdapter(
  coroutineScope: CoroutineScope,
  dispatcherProvider: DispatcherProvider,
) :
  SearchableRepositoryAdapter<PasswordItemRecyclerAdapter.PasswordItemViewHolder>(
    R.layout.password_row_layout,
    ::PasswordItemViewHolder,
    coroutineScope,
    dispatcherProvider,
    PasswordItemViewHolder::bind,
  ) {

  fun makeSelectable(recyclerView: RecyclerView) {
    makeSelectable(recyclerView, ::PasswordItemDetailsLookup)
  }

  override fun onItemClicked(
    listener: (holder: PasswordItemViewHolder, item: PasswordItem) -> Unit
  ): PasswordItemRecyclerAdapter {
    return super.onItemClicked(listener) as PasswordItemRecyclerAdapter
  }

  override fun onSelectionChanged(
    listener: (selection: Selection<String>) -> Unit
  ): PasswordItemRecyclerAdapter {
    return super.onSelectionChanged(listener) as PasswordItemRecyclerAdapter
  }

  class PasswordItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    private val name: AppCompatTextView = itemView.findViewById(R.id.label)
    private val childCount: AppCompatTextView = itemView.findViewById(R.id.child_count)
    private val folderIndicator: AppCompatImageView = itemView.findViewById(R.id.folder_indicator)
    var itemDetails: ItemDetailsLookup.ItemDetails<String>? = null

    suspend fun bind(item: PasswordItem, dispatcherProvider: DispatcherProvider) {
      val parentPath = item.fullPathToParent.replace("(^/)|(/$)".toRegex(), "")
      val source =
        if (parentPath.isNotEmpty()) {
          "$parentPath\n$item"
        } else {
          "$item"
        }
      val spannable = SpannableString(source)
      spannable.setSpan(RelativeSizeSpan(0.7f), 0, parentPath.length, 0)
      name.text = spannable
      if (item.type == PasswordItem.TYPE_CATEGORY) {
        folderIndicator.visibility = View.VISIBLE
        val count =
          withContext(dispatcherProvider.io()) {
            item.file.listFiles { path -> path.isDirectory || path.extension == "gpg" }?.size ?: 0
          }
        childCount.visibility = if (count > 0) View.VISIBLE else View.GONE
        childCount.text = "$count"
      } else {
        childCount.visibility = View.GONE
        folderIndicator.visibility = View.GONE
      }
      itemDetails =
        object : ItemDetailsLookup.ItemDetails<String>() {
          override fun getPosition() = absoluteAdapterPosition

          override fun getSelectionKey() = item.stableId
        }
    }
  }

  class PasswordItemDetailsLookup(private val recyclerView: RecyclerView) :
    ItemDetailsLookup<String>() {

    override fun getItemDetails(event: MotionEvent): ItemDetails<String>? {
      val view = recyclerView.findChildViewUnder(event.x, event.y) ?: return null
      return (recyclerView.getChildViewHolder(view) as PasswordItemViewHolder).itemDetails
    }
  }
}
