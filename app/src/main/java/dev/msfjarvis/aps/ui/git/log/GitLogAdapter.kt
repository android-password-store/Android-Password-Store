/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.git.log

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.ajalt.timberkt.e
import dev.msfjarvis.aps.databinding.GitLogRowLayoutBinding
import dev.msfjarvis.aps.util.git.GitCommit
import dev.msfjarvis.aps.util.git.GitLogModel
import java.text.DateFormat
import java.util.Date

private fun shortHash(hash: String): String {
    return hash.substring(0 until 8)
}

private fun stringFrom(date: Date): String {
    return DateFormat.getDateTimeInstance().format(date)
}

/**
 * @see GitLogActivity
 */
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
            e { "There is no git commit for view holder at position $position." }
            return
        }
        viewHolder.bind(commit)
    }

    override fun getItemCount() = model.size

    class ViewHolder(private val binding: GitLogRowLayoutBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(commit: GitCommit) = with(binding) {
            gitLogRowMessage.text = commit.shortMessage
            gitLogRowHash.text = shortHash(commit.hash)
            gitLogRowTime.text = stringFrom(commit.time)
        }
    }
}
