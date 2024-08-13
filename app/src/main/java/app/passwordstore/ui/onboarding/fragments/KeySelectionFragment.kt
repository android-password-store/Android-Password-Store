/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.onboarding.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.databinding.FragmentKeySelectionBinding
import app.passwordstore.injection.prefs.SettingsPreferences
import app.passwordstore.ui.pgp.PGPKeyListActivity
import app.passwordstore.util.coroutines.DispatcherProvider
import app.passwordstore.util.extensions.commitChange
import app.passwordstore.util.extensions.finish
import app.passwordstore.util.extensions.snackbar
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.settings.PreferenceKeys
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class KeySelectionFragment : Fragment(R.layout.fragment_key_selection) {

  @Inject @SettingsPreferences lateinit var settings: SharedPreferences
  @Inject lateinit var dispatcherProvider: DispatcherProvider
  private val binding by viewBinding(FragmentKeySelectionBinding::bind)
  private val gpgKeySelectAction =
    registerForActivityResult(StartActivityForResult()) { result ->
      if (result.resultCode == AppCompatActivity.RESULT_OK) {
        val data = result.data ?: return@registerForActivityResult
        val selectedKey =
          data.getStringExtra(PGPKeyListActivity.EXTRA_SELECTED_KEY)
            ?: return@registerForActivityResult
        lifecycleScope.launch {
          withContext(dispatcherProvider.io()) {
            val gpgIdentifierFile = File(PasswordRepository.getRepositoryDirectory(), ".gpg-id")
            gpgIdentifierFile.writeText(selectedKey)
          }
          settings.edit { putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, true) }
          requireActivity()
            .commitChange(getString(R.string.git_commit_gpg_id, getString(R.string.app_name)))
          finish()
        }
      } else {
        requireActivity()
          .snackbar(
            message = getString(R.string.gpg_key_select_mandatory),
            length = Snackbar.LENGTH_LONG,
          )
      }
    }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.selectKey.setOnClickListener {
      gpgKeySelectAction.launch(PGPKeyListActivity.newSelectionActivity(requireContext()))
    }
  }

  companion object {

    fun newInstance() = KeySelectionFragment()
  }
}
