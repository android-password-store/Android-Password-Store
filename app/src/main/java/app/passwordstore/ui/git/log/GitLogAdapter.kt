/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.git.log

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.passwordstore.databinding.GitLogRowLayoutBinding
import app.passwordstore.util.git.GitCommit
import app.passwordstore.util.git.GitLogModel
import java.time.Instant
import java.time.format.DateTimeFormatter
import logcat.LogPriority.ERROR
import logcat.logcat

private fun shortHash(hash: String): String {
  return hash.substring(0 until 8)
}

private fun stringFrom(date: Instant): String {
  return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(date)
}

/** @see GitLogActivity */
class GitLogAdapter : RecyclerView.Adapter<GitLogAdapter.ViewHolder>() {

  private val model = GitLogModel()

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val binding = GitLogRowLayoutBinding.inflate(inflater, parent, false)
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
    val commit = model.get(position)
    if (commit == null) {
      logcat(ERROR) { "There is no git commit for view holder at position $position." }
      return
    }
    viewHolder.bind(commit)
  }

  override fun getItemCount() = model.size

  class ViewHolder(private val binding: GitLogRowLayoutBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(commit: GitCommit) =
      with(binding) {
        gitLogRowMessage.text = commit.shortMessage
        gitLogRowHash.text = shortHash(commit.hash)
        gitLogRowTime.text = stringFrom(commit.time)
      }
  }
}
