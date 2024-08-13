/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.onboarding.activity

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import app.passwordstore.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity(R.layout.activity_onboarding) {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportActionBar?.hide()
    val callback = onBackPressedDispatcher.addCallback(enabled = false) { finishAffinity() }
    supportFragmentManager.addOnBackStackChangedListener {
      if (supportFragmentManager.backStackEntryCount == 0) {
        callback.isEnabled = true
      }
    }
  }
}
