/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.git.log

import android.os.Bundle
import android.view.MenuItem
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dev.msfjarvis.aps.databinding.ActivityGitLogBinding
import dev.msfjarvis.aps.ui.git.base.BaseGitActivity
import dev.msfjarvis.aps.util.extensions.viewBinding

/**
 * Displays the repository's git commits in git-log fashion.
 *
 * It provides basic information about each commit by way of a non-interactive RecyclerView.
 */
class GitLogActivity : BaseGitActivity() {

    private val binding by viewBinding(ActivityGitLogBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        createRecyclerView()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun createRecyclerView() {
        binding.gitLogRecyclerView.apply {
            setHasFixedSize(true)
            addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
            adapter = GitLogAdapter()
        }
    }
}
