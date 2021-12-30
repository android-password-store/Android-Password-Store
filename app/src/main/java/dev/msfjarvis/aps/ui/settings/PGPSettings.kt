/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.settings

import androidx.fragment.app.FragmentActivity
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.pref
import dev.msfjarvis.aps.ui.pgp.PGPKeyImportActivity
import dev.msfjarvis.aps.util.extensions.launchActivity

class PGPSettings(private val activity: FragmentActivity) : SettingsProvider {

  override fun provideSettings(builder: PreferenceScreen.Builder) {
    builder.apply {
      pref("_") {
        title = "Import PGP key"
        persistent = false
        onClick {
          activity.launchActivity(PGPKeyImportActivity::class.java)
          false
        }
      }
    }
  }
}
