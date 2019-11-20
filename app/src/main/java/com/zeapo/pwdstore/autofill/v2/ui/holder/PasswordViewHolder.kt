/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.v2.ui.holder

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zeapo.pwdstore.R

class PasswordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val typeImage: ImageView = itemView.findViewById(R.id.type_image)
    val label: TextView = itemView.findViewById(R.id.label)
}
