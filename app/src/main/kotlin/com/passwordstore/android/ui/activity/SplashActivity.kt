/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.ui.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.passwordstore.android.databinding.ActivitySplashBinding
import com.passwordstore.android.ui.EdgeToEdge
import com.passwordstore.android.utils.Constants.IS_FIRST_RUN

class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        EdgeToEdge.setUpRoot(binding.root as ViewGroup)
        setContentView(binding.root)
        if (prefs.getBoolean(IS_FIRST_RUN, true)) {
            startFirstRunFlow()
        } else {
        }
    }

    private fun startFirstRunFlow() {
        startActivity(Intent(this, FirstRunActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
