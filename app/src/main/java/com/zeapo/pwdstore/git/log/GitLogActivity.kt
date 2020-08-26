/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.git.log

import android.os.Bundle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.ActivityGitLogBinding
import com.zeapo.pwdstore.git.BaseGitActivity
import com.zeapo.pwdstore.utils.viewBinding

/**
 * Displays the repository's git commits in git-log fashion.
 *
 * It provides basic information about each commit by way of a non-interactive RecyclerView.
 */
class GitLogActivity : BaseGitActivity() {

    private val binding by viewBinding(ActivityGitLogBinding::inflate)
    private lateinit var view: RecyclerView
    private lateinit var viewLayoutManager: RecyclerView.LayoutManager
    private lateinit var viewAdapter: RecyclerView.Adapter<*>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        createRecyclerView()
    }

    private fun createRecyclerView() {
        viewLayoutManager = LinearLayoutManager(this)
        viewAdapter = GitLogAdapter()
        val viewItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        view = findViewById<RecyclerView>(R.id.git_log_recycler_view).apply {
            setHasFixedSize(true)
            addItemDecoration(viewItemDecoration)
            layoutManager = viewLayoutManager
            adapter = viewAdapter
        }
    }
}