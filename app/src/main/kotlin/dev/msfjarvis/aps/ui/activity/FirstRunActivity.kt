/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.activity

import android.os.Bundle
import android.view.ViewGroup
import dev.msfjarvis.aps.databinding.ActivityFirstRunBinding
import dev.msfjarvis.aps.ui.EdgeToEdge

class FirstRunActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityFirstRunBinding.inflate(layoutInflater)
        EdgeToEdge.setUpRoot(binding.root as ViewGroup)
        setContentView(binding.root)
    }
}
