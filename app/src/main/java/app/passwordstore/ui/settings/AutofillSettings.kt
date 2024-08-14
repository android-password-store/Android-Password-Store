/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.passwordstore.BuildConfig
import app.passwordstore.R
import app.passwordstore.util.extensions.autofillManager
import app.passwordstore.util.settings.PreferenceKeys
import com.github.androidpasswordstore.autofillparser.BrowserAutofillSupportLevel
import com.github.androidpasswordstore.autofillparser.getInstalledBrowsersWithAutofillSupportLevel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.editText
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.switch
import de.Maxr1998.modernpreferences.preferences.SwitchPreference

class AutofillSettings(private val activity: FragmentActivity) : SettingsProvider {

  private val isAutofillServiceEnabled: Boolean
    get() {
      return activity.autofillManager?.hasEnabledAutofillServices() == true
    }

  private fun showAutofillDialog(pref: SwitchPreference) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          pref.checked = isAutofillServiceEnabled
        }
        else -> {}
      }
    }
    MaterialAlertDialogBuilder(activity).run {
      setTitle(R.string.pref_autofill_enable_title)
      @SuppressLint("InflateParams")
      val layout = activity.layoutInflater.inflate(R.layout.oreo_autofill_instructions, null)
      val supportedBrowsersTextView = layout.findViewById<AppCompatTextView>(R.id.supportedBrowsers)
      supportedBrowsersTextView.text =
        getInstalledBrowsersWithAutofillSupportLevel(context).joinToString(separator = "\n") {
          val appLabel = it.first
          val supportDescription =
            when (it.second) {
              BrowserAutofillSupportLevel.None ->
                activity.getString(R.string.oreo_autofill_no_support)
              BrowserAutofillSupportLevel.FlakyFill ->
                activity.getString(R.string.oreo_autofill_flaky_fill_support)
              BrowserAutofillSupportLevel.PasswordFill ->
                activity.getString(R.string.oreo_autofill_password_fill_support)
              BrowserAutofillSupportLevel.PasswordFillAndSaveIfNoAccessibility ->
                activity.getString(
                  R.string.oreo_autofill_password_fill_and_conditional_save_support
                )
              BrowserAutofillSupportLevel.GeneralFill ->
                activity.getString(R.string.oreo_autofill_general_fill_support)
              BrowserAutofillSupportLevel.GeneralFillAndSave ->
                activity.getString(R.string.oreo_autofill_general_fill_and_save_support)
            }
          "$appLabel: $supportDescription"
        }
      setView(layout)
      setPositiveButton(R.string.dialog_ok) { _, _ ->
        val intent =
          Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
            data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
          }
        activity.startActivity(intent)
      }
      setNegativeButton(R.string.dialog_cancel, null)
      setOnDismissListener { pref.checked = isAutofillServiceEnabled }
      activity.lifecycle.addObserver(observer)
      show()
    }
  }

  override fun provideSettings(builder: PreferenceScreen.Builder) {
    builder.apply {
      switch(PreferenceKeys.AUTOFILL_ENABLE) {
        titleRes = R.string.pref_autofill_enable_title
        defaultValue = isAutofillServiceEnabled
        onClick {
          if (isAutofillServiceEnabled) {
            activity.autofillManager?.disableAutofillServices()
          } else {
            showAutofillDialog(this)
          }
          false
        }
      }
      editText(PreferenceKeys.OREO_AUTOFILL_DEFAULT_USERNAME) {
        dependency = PreferenceKeys.AUTOFILL_ENABLE
        titleRes = R.string.preference_default_username_title
        summaryProvider = { activity.getString(R.string.preference_default_username_summary) }
      }
      editText(PreferenceKeys.OREO_AUTOFILL_CUSTOM_PUBLIC_SUFFIXES) {
        dependency = PreferenceKeys.AUTOFILL_ENABLE
        titleRes = R.string.preference_custom_public_suffixes_title
        summaryProvider = { activity.getString(R.string.preference_custom_public_suffixes_summary) }
        textInputHintRes = R.string.preference_custom_public_suffixes_hint
      }
    }
  }
}
