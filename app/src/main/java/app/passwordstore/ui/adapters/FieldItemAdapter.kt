/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.adapters

import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import app.passwordstore.R
import app.passwordstore.data.passfile.Totp
import app.passwordstore.data.password.FieldItem
import app.passwordstore.databinding.ItemFieldBinding
import app.passwordstore.ui.compose.R as composeR
import com.google.android.material.textfield.TextInputLayout

class FieldItemAdapter(
  private var fieldItemList: List<FieldItem>,
  private val showPassword: Boolean,
  private val copyTextToClipboard: (text: String?) -> Unit,
) : RecyclerView.Adapter<FieldItemAdapter.FieldItemViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FieldItemViewHolder {
    val binding = ItemFieldBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return FieldItemViewHolder(binding.root, binding)
  }

  override fun onBindViewHolder(holder: FieldItemViewHolder, position: Int) {
    holder.bind(fieldItemList[position], showPassword, copyTextToClipboard)
  }

  override fun getItemCount(): Int {
    return fieldItemList.size
  }

  fun updateOTPCode(totp: Totp, labelFormat: String) {
    var otpItemPosition = -1
    fieldItemList =
      fieldItemList.mapIndexed { position, item ->
        if (item.type == FieldItem.ItemType.OTP) {
          otpItemPosition = position
          return@mapIndexed FieldItem.createOtpField(labelFormat, totp)
        }

        return@mapIndexed item
      }

    notifyItemChanged(otpItemPosition)
  }

  class FieldItemViewHolder(itemView: View, val binding: ItemFieldBinding) :
    RecyclerView.ViewHolder(itemView) {

    fun bind(fieldItem: FieldItem, showPassword: Boolean, copyTextToClipboard: (String?) -> Unit) {
      with(binding) {
        itemText.hint = fieldItem.label
        itemTextContainer.hint = fieldItem.label
        itemText.setText(fieldItem.value)

        when (fieldItem.action) {
          FieldItem.ActionType.COPY -> {
            itemTextContainer.apply {
              endIconDrawable =
                ContextCompat.getDrawable(itemView.context, R.drawable.ic_content_copy)
              endIconMode = TextInputLayout.END_ICON_CUSTOM
              setEndIconOnClickListener { copyTextToClipboard(itemText.text.toString()) }
            }
            itemText.transformationMethod = null
          }
          FieldItem.ActionType.HIDE -> {
            itemTextContainer.apply {
              endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
              setOnClickListener { copyTextToClipboard(itemText.text.toString()) }
            }
            itemText.apply {
              transformationMethod =
                if (!showPassword) {
                  PasswordTransformationMethod.getInstance()
                } else {
                  null
                }
              if (fieldItem.type == FieldItem.ItemType.PASSWORD) {
                typeface =
                  ResourcesCompat.getFont(
                    binding.root.context,
                    composeR.font.jetbrainsmono_nl_regular,
                  )
              }
              setOnClickListener { copyTextToClipboard(itemText.text.toString()) }
            }
          }
        }
      }
    }
  }
}
