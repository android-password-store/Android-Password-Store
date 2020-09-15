/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.ui.onboarding.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zeapo.pwdstore.R

class OnboardingActivity : AppCompatActivity(R.layout.activity_onboarding) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount == 0) {
            finishAffinity()
        } else {
            super.onBackPressed()
        }
    }
}
