/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo.ui

import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.autofill.oreo.AutofillMatcher
import com.zeapo.pwdstore.autofill.oreo.AutofillPublisherChangedException
import com.zeapo.pwdstore.autofill.oreo.FormOrigin
import com.zeapo.pwdstore.autofill.oreo.computeCertificatesHash
import kotlinx.android.synthetic.main.activity_oreo_autofill_publisher_changed.*

@TargetApi(Build.VERSION_CODES.O)
class AutofillPublisherChangedActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_APP_PACKAGE =
            "com.zeapo.pwdstore.autofill.oreo.ui.EXTRA_APP_PACKAGE"
        private var publisherChangedRequestCode = 1

        fun makePublisherChangedIntentSender(
            context: Context,
            publisherChangedException: AutofillPublisherChangedException
        ): IntentSender {
            val intent = Intent(context, AutofillPublisherChangedActivity::class.java).apply {
                putExtra(EXTRA_APP_PACKAGE, publisherChangedException.formOrigin.identifier)
            }
            return PendingIntent.getActivity(
                context, publisherChangedRequestCode++, intent, PendingIntent.FLAG_CANCEL_CURRENT
            ).intentSender
        }
    }

    private lateinit var appPackage: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oreo_autofill_publisher_changed)
        setFinishOnTouchOutside(true)

        appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE) ?: run {
            e { "AutofillPublisherChangedActivity started without EXTRA_PACKAGE_NAME" }
            finish()
            return
        }
        supportActionBar?.hide()
        showPackageInfo()

        okButton.setOnClickListener { finish() }
        advancedButton.setOnClickListener {
            advancedButton.visibility = View.INVISIBLE
            warningAppAdvancedInfo.visibility = View.VISIBLE
            resetButton.visibility = View.VISIBLE
        }
        resetButton.setOnClickListener {
            AutofillMatcher.clearMatchesFor(this, FormOrigin.App(appPackage))
            finish()
        }
    }

    private fun showPackageInfo() {
        try {
            val packageInfo =
                packageManager.getPackageInfo(appPackage, PackageManager.GET_META_DATA)
            val installTime = DateUtils.getRelativeTimeSpanString(packageInfo.firstInstallTime)
            warningAppInstallDate.text =
                getString(R.string.oreo_autofill_warning_publisher_install_time, installTime)
            val appInfo =
                packageManager.getApplicationInfo(appPackage, PackageManager.GET_META_DATA)
            warningAppName.text = "“${packageManager.getApplicationLabel(appInfo)}”"

            val currentHash = computeCertificatesHash(this, appPackage)
            warningAppAdvancedInfo.text = getString(
                R.string.oreo_autofill_warning_publisher_advanced_info_template,
                appPackage,
                currentHash
            )
        } catch (exception: Exception) {
            e(exception) { "Failed to retrieve package info for $appPackage" }
            finish()
        }
    }
}
