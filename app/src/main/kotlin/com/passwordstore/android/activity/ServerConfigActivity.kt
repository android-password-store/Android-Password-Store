/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.activity

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.passwordstore.android.databinding.ActivityServerConfigBinding
import com.passwordstore.android.ui.EdgeToEdge

class ServerConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityServerConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerConfigBinding.inflate(layoutInflater)
        EdgeToEdge.setUpRoot(binding.root as ViewGroup)
        EdgeToEdge.setUpAppBar(binding.serverConfigAppbar, binding.serverConfigToolbar)
        setContentView(binding.root)
    }
}
