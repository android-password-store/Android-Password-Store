/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.settings

import androidx.fragment.app.FragmentActivity
import app.passwordstore.R
import app.passwordstore.ui.pgp.PGPKeyListActivity
import app.passwordstore.util.extensions.launchActivity
import app.passwordstore.util.features.Feature
import app.passwordstore.util.settings.PreferenceKeys
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.switch

class PGPSettings(private val activity: FragmentActivity) : SettingsProvider {

  override fun provideSettings(builder: PreferenceScreen.Builder) {
    builder.apply {
      pref("_") {
        titleRes = R.string.pref_pgp_key_manager_title
        persistent = false
        onClick {
          activity.launchActivity(PGPKeyListActivity::class.java)
          false
        }
      }
      switch(PreferenceKeys.ASCII_ARMOR) {
        titleRes = R.string.pref_pgp_ascii_armor_title
        persistent = true
      }
      switch(Feature.EnablePGPPassphraseCache.configKey) {
        titleRes = R.string.pref_passphrase_cache_title
        summaryRes = R.string.pref_passphrase_cache_summary
        defaultValue = false
      }
    }
  }
}
