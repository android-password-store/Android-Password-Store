/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.settings

import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.editText
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.singleChoice
import de.Maxr1998.modernpreferences.helpers.switch
import de.Maxr1998.modernpreferences.preferences.choice.SelectionItem
import dev.msfjarvis.aps.BuildConfig
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.util.extensions.autofillManager
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.FragmentActivity
import com.github.androidpasswordstore.autofillparser.BrowserAutofillSupportLevel
import com.github.androidpasswordstore.autofillparser.getInstalledBrowsersWithAutofillSupportLevel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AutofillSettings(private val activity: FragmentActivity) : SettingsProvider {

    private val isAutofillServiceEnabled: Boolean
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return activity.autofillManager?.hasEnabledAutofillServices() == true
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showAutofillDialog() {
        MaterialAlertDialogBuilder(activity).run {
            setTitle(R.string.pref_autofill_enable_title)
            @SuppressLint("InflateParams")
            val layout =
                activity.layoutInflater.inflate(R.layout.oreo_autofill_instructions, null)
            val supportedBrowsersTextView =
                layout.findViewById<AppCompatTextView>(R.id.supportedBrowsers)
            supportedBrowsersTextView.text =
                getInstalledBrowsersWithAutofillSupportLevel(context).joinToString(
                    separator = "\n"
                ) {
                    val appLabel = it.first
                    val supportDescription = when (it.second) {
                        BrowserAutofillSupportLevel.None -> activity.getString(R.string.oreo_autofill_no_support)
                        BrowserAutofillSupportLevel.FlakyFill -> activity.getString(R.string.oreo_autofill_flaky_fill_support)
                        BrowserAutofillSupportLevel.PasswordFill -> activity.getString(R.string.oreo_autofill_password_fill_support)
                        BrowserAutofillSupportLevel.PasswordFillAndSaveIfNoAccessibility -> activity.getString(R.string.oreo_autofill_password_fill_and_conditional_save_support)
                        BrowserAutofillSupportLevel.GeneralFill -> activity.getString(R.string.oreo_autofill_general_fill_support)
                        BrowserAutofillSupportLevel.GeneralFillAndSave -> activity.getString(R.string.oreo_autofill_general_fill_and_save_support)
                    }
                    "$appLabel: $supportDescription"
                }
            setView(layout)
            setPositiveButton(R.string.dialog_ok) { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                    data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                }
                activity.startActivity(intent)
            }
            setNegativeButton(R.string.dialog_cancel, null)
            show()
        }
    }

    override fun provideSettings(builder: PreferenceScreen.Builder) {
        builder.apply {
            switch(PreferenceKeys.AUTOFILL_ENABLE) {
                titleRes = R.string.pref_autofill_enable_title
                visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                defaultValue = false
                onClick {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@onClick true
                    if (isAutofillServiceEnabled) {
                        activity.autofillManager?.disableAutofillServices()
                    } else {
                        showAutofillDialog()
                    }
                    true
                }
            }
            val values = activity.resources.getStringArray(R.array.oreo_autofill_directory_structure_values)
            val titles = activity.resources.getStringArray(R.array.oreo_autofill_directory_structure_entries)
            val items = values.zip(titles).map { SelectionItem(it.first, it.second, null) }
            singleChoice(PreferenceKeys.OREO_AUTOFILL_DIRECTORY_STRUCTURE, items) {
                dependency = PreferenceKeys.AUTOFILL_ENABLE
                titleRes = R.string.oreo_autofill_preference_directory_structure
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
