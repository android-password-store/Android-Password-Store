/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.adapters

import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.data.password.FieldItem
import dev.msfjarvis.aps.databinding.ItemFieldBinding

class FieldItemAdapter(
  private var fieldItemList: List<FieldItem>,
  private val showPassword: Boolean,
  private val copyTextToClipBoard: (text: String?) -> Unit,
) : RecyclerView.Adapter<FieldItemAdapter.FieldItemViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldItemViewHolder {
    val binding = ItemFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return FieldItemViewHolder(binding.root, binding)
  }

  override fun onBindViewHolder(holder: FieldItemViewHolder, position: Int) {
    holder.bind(fieldItemList[position], showPassword, copyTextToClipBoard)
  }

  override fun getItemCount(): Int {
    return fieldItemList.size
  }

  fun updateOTPCode(code: String) {
    var otpItemPosition = -1
    fieldItemList =
      fieldItemList.mapIndexed { position, item ->
        if (item.key.equals(FieldItem.ItemType.OTP.type, true)) {
          otpItemPosition = position
          return@mapIndexed FieldItem.createOtpField(code)
        }

        return@mapIndexed item
      }

    notifyItemChanged(otpItemPosition)
  }

  fun updateItems(itemList: List<FieldItem>) {
    fieldItemList = itemList
    notifyDataSetChanged()
  }

  class FieldItemViewHolder(itemView: View, val binding: ItemFieldBinding) :
    RecyclerView.ViewHolder(itemView) {

    fun bind(fieldItem: FieldItem, showPassword: Boolean, copyTextToClipBoard: (String?) -> Unit) {
      with(binding) {
        itemText.hint = fieldItem.key
        itemTextContainer.hint = fieldItem.key
        itemText.setText(fieldItem.value)

        when (fieldItem.action) {
          FieldItem.ActionType.COPY -> {
            itemTextContainer.apply {
              endIconDrawable =
                ContextCompat.getDrawable(itemView.context, R.drawable.ic_content_copy)
              endIconMode = TextInputLayout.END_ICON_CUSTOM
              setEndIconOnClickListener { copyTextToClipBoard(itemText.text.toString()) }
            }
          }
          FieldItem.ActionType.HIDE -> {
            itemTextContainer.apply {
              endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
              setOnClickListener { copyTextToClipBoard(itemText.text.toString()) }
            }
            itemText.apply {
              if (!showPassword) {
                transformationMethod = PasswordTransformationMethod.getInstance()
              }
              setOnClickListener { copyTextToClipBoard(itemText.text.toString()) }
            }
          }
        }
      }
    }
  }
}
