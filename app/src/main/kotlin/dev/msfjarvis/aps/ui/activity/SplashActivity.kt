/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import dev.msfjarvis.aps.databinding.ActivitySplashBinding
import dev.msfjarvis.aps.ui.EdgeToEdge
import dev.msfjarvis.aps.utils.PreferenceKeys.IS_FIRST_RUN

class SplashActivity : AppCompatActivity() {

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
    }
  }

  private fun startFirstRunFlow() {
    startActivity(Intent(this, FirstRunActivity::class.java))
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    finish()
  }
}
