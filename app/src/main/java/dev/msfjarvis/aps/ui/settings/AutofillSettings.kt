/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.settings

import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.singleChoice
import de.Maxr1998.modernpreferences.helpers.switch
import de.Maxr1998.modernpreferences.preferences.choice.SelectionItem
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import androidx.fragment.app.FragmentActivity

class AutofillSettings(val activity: FragmentActivity) : SettingsProvider {

    override fun provideSettings(builder: PreferenceScreen.Builder) {
        builder.apply {
            switch(PreferenceKeys.AUTOFILL_ENABLE) {
                titleRes = R.string.pref_autofill_enable_title

                defaultValue = true
            }
            // TODO(msfjarvis): fix the value being committed here
            val values = activity.resources.getStringArray(R.array.oreo_autofill_directory_structure_values)
            val titles = activity.resources.getStringArray(R.array.oreo_autofill_directory_structure_entries)
            check(values.size == titles.size)
            val items = values.mapIndexed {
                index, value -> SelectionItem(key = value, title = titles[index], null)
            }
            singleChoice(
                PreferenceKeys.OREO_AUTOFILL_DIRECTORY_STRUCTURE,
                items,
            ) {
                titleRes = R.string.oreo_autofill_preference_directory_structure
            }
            // TODO(msfjarvis): add back oreo_autofill_default_username
            // TODO(msfjarvis): add back oreo_autofill_custom_public_suffixes
        }
    }
}
