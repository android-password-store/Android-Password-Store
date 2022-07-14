/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.onboarding.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.databinding.FragmentKeySelectionBinding
import app.passwordstore.ui.crypto.GetKeyIdsActivity
import app.passwordstore.util.extensions.commitChange
import app.passwordstore.util.extensions.finish
import app.passwordstore.util.extensions.sharedPrefs
import app.passwordstore.util.extensions.snackbar
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.settings.PreferenceKeys
import com.google.android.material.snackbar.Snackbar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.msfjarvis.openpgpktx.util.OpenPgpApi

class KeySelectionFragment : Fragment(R.layout.fragment_key_selection) {

  private val settings by unsafeLazy { requireActivity().applicationContext.sharedPrefs }
  private val binding by viewBinding(FragmentKeySelectionBinding::bind)

  private val gpgKeySelectAction =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == AppCompatActivity.RESULT_OK) {
        result.data?.getStringArrayExtra(OpenPgpApi.EXTRA_KEY_IDS)?.let { keyIds ->
          lifecycleScope.launch {
            withContext(Dispatchers.IO) {
              val gpgIdentifierFile = File(PasswordRepository.getRepositoryDirectory(), ".gpg-id")
              gpgIdentifierFile.writeText((keyIds + "").joinToString("\n"))
            }
            settings.edit { putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, true) }
            requireActivity()
              .commitChange(getString(R.string.git_commit_gpg_id, getString(R.string.app_name)))
          }
        }
        finish()
      } else {
        requireActivity()
          .snackbar(
            message = getString(R.string.gpg_key_select_mandatory),
            length = Snackbar.LENGTH_LONG
          )
      }
    }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.selectKey.setOnClickListener {
      gpgKeySelectAction.launch(Intent(requireContext(), GetKeyIdsActivity::class.java))
    }
  }

  companion object {

    fun newInstance() = KeySelectionFragment()
  }
}
