/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BundleCompat
import app.passwordstore.R
import app.passwordstore.data.crypto.PGPPassphraseCache
import app.passwordstore.databinding.ActivityPreferenceRecyclerviewBinding
import app.passwordstore.util.extensions.viewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferencesAdapter
import de.Maxr1998.modernpreferences.helpers.screen
import de.Maxr1998.modernpreferences.helpers.subScreen
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

  @Inject lateinit var passphraseCache: PGPPassphraseCache
  private val miscSettings = MiscSettings(this)
  private val autofillSettings = AutofillSettings(this)
  private val passwordSettings = PasswordSettings(this)
  private val repositorySettings = RepositorySettings(this)
  private val generalSettings = GeneralSettings(this)
  private lateinit var pgpSettings: PGPSettings

  private val binding by viewBinding(ActivityPreferenceRecyclerviewBinding::inflate)
  private val preferencesAdapter: PreferencesAdapter
    get() = binding.preferenceRecyclerView.adapter as PreferencesAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)
    Preference.Config.dialogBuilderFactory = { context -> MaterialAlertDialogBuilder(context) }
    pgpSettings = PGPSettings(this, passphraseCache)
    val screen =
      screen(this) {
        subScreen {
          collapseIcon = true
          titleRes = R.string.pref_category_general_title
          iconRes = R.drawable.app_settings_alt_24px
          generalSettings.provideSettings(this)
        }
        subScreen {
          collapseIcon = true
          titleRes = R.string.pref_category_autofill_title
          iconRes = R.drawable.ic_wysiwyg_24px
          autofillSettings.provideSettings(this)
        }
        subScreen {
          collapseIcon = true
          titleRes = R.string.pref_category_passwords_title
          iconRes = R.drawable.ic_password_24px
          passwordSettings.provideSettings(this)
        }
        subScreen {
          collapseIcon = true
          titleRes = R.string.pref_category_repository_title
          iconRes = R.drawable.ic_call_merge_24px
          repositorySettings.provideSettings(this)
        }
        subScreen {
          collapseIcon = true
          titleRes = R.string.pref_category_misc_title
          iconRes = R.drawable.ic_miscellaneous_services_24px
          miscSettings.provideSettings(this)
        }
        subScreen {
          collapseIcon = true
          titleRes = R.string.pref_category_pgp_title
          iconRes = R.drawable.ic_lock_open_24px
          pgpSettings.provideSettings(this)
        }
      }
    val backPressedCallback =
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          preferencesAdapter.goBack()
        }
      }
    onBackPressedDispatcher.addCallback(backPressedCallback)
    val adapter = PreferencesAdapter(screen)
    adapter.onScreenChangeListener =
      PreferencesAdapter.OnScreenChangeListener { subScreen, entering ->
        backPressedCallback.isEnabled = entering
        supportActionBar?.title =
          if (!entering) {
            getString(R.string.action_settings)
          } else {
            getString(subScreen.titleRes)
          }
      }
    if (savedInstanceState != null) {
      BundleCompat.getParcelable(
          savedInstanceState,
          "adapter",
          PreferencesAdapter.SavedState::class.java,
        )
        ?.let(adapter::loadSavedState)
    }
    binding.preferenceRecyclerView.adapter = adapter
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putParcelable("adapter", preferencesAdapter.getSavedState())
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      android.R.id.home ->
        if (!preferencesAdapter.goBack()) {
          super.onOptionsItemSelected(item)
        } else {
          true
        }
      else -> super.onOptionsItemSelected(item)
    }
  }
}
