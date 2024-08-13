/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.autofill

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.passwordstore.R

class PasswordViewHolder(view: View) : RecyclerView.ViewHolder(view) {

  val title: TextView = itemView.findViewById(R.id.title)
  val subtitle: TextView = itemView.findViewById(R.id.subtitle)
}
