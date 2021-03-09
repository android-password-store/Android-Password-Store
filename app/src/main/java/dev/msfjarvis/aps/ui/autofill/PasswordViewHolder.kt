/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.autofill

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import dev.msfjarvis.aps.R

class PasswordViewHolder(view: View) : RecyclerView.ViewHolder(view) {

  val title: TextView = itemView.findViewById(R.id.title)
  val subtitle: TextView = itemView.findViewById(R.id.subtitle)
}
