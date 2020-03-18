/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.splash.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import dev.msfjarvis.aps.databinding.ActivitySplashBinding
import dev.msfjarvis.aps.di.injector
import dev.msfjarvis.aps.ui.EdgeToEdge
import dev.msfjarvis.aps.ui.firstrun.activity.FirstRunActivity
import dev.msfjarvis.aps.utils.PreferenceKeys.IS_FIRST_RUN
import javax.inject.Inject

class SplashActivity : AppCompatActivity() {

  private lateinit var binding: ActivitySplashBinding
  @Inject lateinit var prefs: SharedPreferences

  override fun onCreate(savedInstanceState: Bundle?) {
    injector.inject(this)
    super.onCreate(savedInstanceState)
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
