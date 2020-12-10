/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.settings

import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.categoryHeader
import de.Maxr1998.modernpreferences.helpers.checkBox
import de.Maxr1998.modernpreferences.helpers.editText
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import android.text.InputType
import androidx.fragment.app.FragmentActivity

class PasswordSettings(private val activity: FragmentActivity) : SettingsProvider {

    override fun provideSettings(builder: PreferenceScreen.Builder) {
        builder.apply {
            editText(PreferenceKeys.GENERAL_SHOW_TIME) {
                titleRes = R.string.pref_clipboard_timeout_title
                summaryProvider = { activity.getString(R.string.pref_clipboard_timeout_summary) }
                textInputType = InputType.TYPE_CLASS_NUMBER
            }
            checkBox(PreferenceKeys.SHOW_PASSWORD) {
                titleRes = R.string.show_password_pref_title
                summaryRes = R.string.show_password_pref_summary
                defaultValue = true
            }
            checkBox(PreferenceKeys.SHOW_EXTRA_CONTENT) {
                titleRes = R.string.show_extra_content_pref_title
                summaryRes = R.string.show_extra_content_pref_summary
                defaultValue = true
            }
            checkBox(PreferenceKeys.COPY_ON_DECRYPT) {
                titleRes = R.string.pref_copy_title
                summaryRes = R.string.pref_copy_summary
                defaultValue = false
            }
        }
    }
}
