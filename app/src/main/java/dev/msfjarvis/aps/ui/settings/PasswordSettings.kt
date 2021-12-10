/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.settings

import android.text.InputType
import androidx.fragment.app.FragmentActivity
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.checkBox
import de.Maxr1998.modernpreferences.helpers.editText
import de.Maxr1998.modernpreferences.helpers.onSelectionChange
import de.Maxr1998.modernpreferences.helpers.singleChoice
import de.Maxr1998.modernpreferences.preferences.choice.SelectionItem
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.util.settings.PreferenceKeys

class PasswordSettings(private val activity: FragmentActivity) : SettingsProvider {

  override fun provideSettings(builder: PreferenceScreen.Builder) {
    builder.apply {
      val values = activity.resources.getStringArray(R.array.pwgen_provider_values)
      val labels = activity.resources.getStringArray(R.array.pwgen_provider_labels)
      val items = values.zip(labels).map { SelectionItem(it.first, it.second, null) }
      singleChoice(
        PreferenceKeys.PREF_KEY_PWGEN_TYPE,
        items,
      ) {
        initialSelection = "classic"
        titleRes = R.string.pref_password_generator_type_title
        onSelectionChange { true }
      }
      editText(PreferenceKeys.GENERAL_SHOW_TIME) {
        titleRes = R.string.pref_clipboard_timeout_title
        summaryProvider =
          { timeout ->
            activity.getString(R.string.pref_clipboard_timeout_summary, timeout ?: "45")
          }
        textInputType = InputType.TYPE_CLASS_NUMBER
      }
      checkBox(PreferenceKeys.SHOW_PASSWORD) {
        titleRes = R.string.show_password_pref_title
        summaryRes = R.string.show_password_pref_summary
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
