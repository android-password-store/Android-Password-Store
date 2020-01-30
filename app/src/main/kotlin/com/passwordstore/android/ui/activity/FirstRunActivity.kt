/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.ui.activity

import android.os.Bundle
import android.view.ViewGroup
import com.passwordstore.android.databinding.ActivityFirstRunBinding
import com.passwordstore.android.ui.EdgeToEdge

class FirstRunActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityFirstRunBinding.inflate(layoutInflater)
        EdgeToEdge.setUpRoot(binding.root as ViewGroup)
        setContentView(binding.root)
    }
}
