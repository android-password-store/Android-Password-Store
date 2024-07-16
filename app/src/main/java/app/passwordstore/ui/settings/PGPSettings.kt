/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.settings

import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.data.crypto.PGPPassphraseCache
import app.passwordstore.ui.pgp.PGPKeyListActivity
import app.passwordstore.util.auth.BiometricAuthenticator
import app.passwordstore.util.extensions.launchActivity
import app.passwordstore.util.extensions.sharedPrefs
import app.passwordstore.util.features.Feature
import app.passwordstore.util.settings.PreferenceKeys
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.onCheckedChange
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.pref
import de.Maxr1998.modernpreferences.helpers.switch
import kotlinx.coroutines.launch

class PGPSettings(
  private val activity: FragmentActivity,
  private val passphraseCache: PGPPassphraseCache,
) : SettingsProvider {

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
        enabled = BiometricAuthenticator.canAuthenticate(activity)
        titleRes = R.string.pref_passphrase_cache_title
        summaryRes = R.string.pref_passphrase_cache_summary
        defaultValue = false
        onCheckedChange { checked ->
          if (checked) {
            if (BiometricAuthenticator.canAuthenticate(activity)) {
              BiometricAuthenticator.authenticate(
                activity,
                R.string.pref_passphrase_cache_authenticate_enable,
              ) {
                if (!(it is BiometricAuthenticator.Result.Success))
                  activity.sharedPrefs.edit {
                    putBoolean(Feature.EnablePGPPassphraseCache.configKey, false)
                  }
              }
            } else
              activity.sharedPrefs.edit {
                putBoolean(Feature.EnablePGPPassphraseCache.configKey, false)
              }
          } else {
            activity.sharedPrefs.edit { remove(PreferenceKeys.CLEAR_PASSPHRASE_CACHE) }
            activity.lifecycleScope.launch { passphraseCache.clearAllCachedPassphrases(activity) }
          }
          true
        }
      }
      switch(PreferenceKeys.CLEAR_PASSPHRASE_CACHE) {
        dependency = Feature.EnablePGPPassphraseCache.configKey
        titleRes = R.string.pref_passphrase_cache_auto_clear_title
        summaryRes = R.string.pref_passphrase_cache_auto_clear_summary
        defaultValue = true
        /* clear cache once when unchecking; this is to prevent a malicious user
         * from bypassing cache clearing via the settings */
        onCheckedChange { checked ->
          if (!checked)
            activity.lifecycleScope.launch { passphraseCache.clearAllCachedPassphrases(activity) }
          true
        }
      }
    }
  }
}
