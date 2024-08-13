/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.autofill.FillResponse
import android.text.format.DateUtils
import android.view.View
import android.view.autofill.AutofillManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import app.passwordstore.R
import app.passwordstore.databinding.ActivityOreoAutofillPublisherChangedBinding
import app.passwordstore.util.autofill.AutofillMatcher
import app.passwordstore.util.autofill.AutofillPublisherChangedException
import app.passwordstore.util.extensions.asLog
import app.passwordstore.util.extensions.getApplicationInfoCompat
import app.passwordstore.util.extensions.getPackageInfoCompat
import app.passwordstore.util.extensions.viewBinding
import com.github.androidpasswordstore.autofillparser.FormOrigin
import com.github.androidpasswordstore.autofillparser.computeCertificatesHash
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import logcat.LogPriority.ERROR
import logcat.logcat

class AutofillPublisherChangedActivity : AppCompatActivity() {

  companion object {

    private const val EXTRA_APP_PACKAGE = "app.passwordstore.autofill.oreo.ui.EXTRA_APP_PACKAGE"
    private const val EXTRA_FILL_RESPONSE_AFTER_RESET =
      "app.passwordstore.autofill.oreo.ui.EXTRA_FILL_RESPONSE_AFTER_RESET"
    private var publisherChangedRequestCode = 1

    fun makePublisherChangedIntentSender(
      context: Context,
      publisherChangedException: AutofillPublisherChangedException,
      fillResponseAfterReset: FillResponse?,
    ): IntentSender {
      val intent =
        Intent(context, AutofillPublisherChangedActivity::class.java).apply {
          putExtra(EXTRA_APP_PACKAGE, publisherChangedException.formOrigin.identifier)
          putExtra(EXTRA_FILL_RESPONSE_AFTER_RESET, fillResponseAfterReset)
        }
      return PendingIntent.getActivity(
          context,
          publisherChangedRequestCode++,
          intent,
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE
          } else {
            PendingIntent.FLAG_CANCEL_CURRENT
          },
        )
        .intentSender
    }
  }

  private lateinit var appPackage: String
  private val binding by viewBinding(ActivityOreoAutofillPublisherChangedBinding::inflate)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)
    setFinishOnTouchOutside(true)

    appPackage =
      intent.getStringExtra(EXTRA_APP_PACKAGE)
        ?: run {
          logcat(ERROR) { "AutofillPublisherChangedActivity started without EXTRA_PACKAGE_NAME" }
          finish()
          return
        }
    supportActionBar?.hide()
    showPackageInfo()
    with(binding) {
      okButton.setOnClickListener { finish() }
      advancedButton.setOnClickListener {
        advancedButton.visibility = View.GONE
        warningAppAdvancedInfo.visibility = View.VISIBLE
        resetButton.visibility = View.VISIBLE
      }
      resetButton.setOnClickListener {
        AutofillMatcher.clearMatchesFor(
          this@AutofillPublisherChangedActivity,
          FormOrigin.App(appPackage),
        )
        val fillResponse =
          IntentCompat.getParcelableExtra(
            intent,
            EXTRA_FILL_RESPONSE_AFTER_RESET,
            FillResponse::class.java,
          )
        setResult(
          RESULT_OK,
          Intent().apply { putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, fillResponse) },
        )
        finish()
      }
    }
  }

  private fun showPackageInfo() {
    runCatching {
        with(binding) {
          val packageInfo =
            packageManager.getPackageInfoCompat(appPackage, PackageManager.GET_META_DATA)
          val installTime = DateUtils.getRelativeTimeSpanString(packageInfo.firstInstallTime)
          warningAppInstallDate.text =
            getString(R.string.oreo_autofill_warning_publisher_install_time, installTime)
          val appInfo =
            packageManager.getApplicationInfoCompat(appPackage, PackageManager.GET_META_DATA)
          warningAppName.text =
            getString(
              R.string.oreo_autofill_warning_publisher_app_name,
              packageManager.getApplicationLabel(appInfo),
            )

          val currentHash =
            computeCertificatesHash(this@AutofillPublisherChangedActivity, appPackage)
          warningAppAdvancedInfo.text =
            getString(
              R.string.oreo_autofill_warning_publisher_advanced_info_template,
              appPackage,
              currentHash,
            )
        }
      }
      .onFailure { e ->
        logcat(ERROR) { e.asLog("Failed to retrieve package info for $appPackage") }
        finish()
      }
  }
}
