/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.onboarding.fragments

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import app.passwordstore.R
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.databinding.FragmentCloneBinding
import app.passwordstore.ui.git.config.GitServerConfigActivity
import app.passwordstore.util.extensions.finish
import app.passwordstore.util.extensions.performTransactionWithBackStack
import app.passwordstore.util.extensions.sharedPrefs
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.settings.PreferenceKeys
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import java.nio.file.LinkOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.notExists
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

class CloneFragment : Fragment(R.layout.fragment_clone) {

  private val binding by viewBinding(FragmentCloneBinding::bind)

  private val settings by unsafeLazy { requireActivity().applicationContext.sharedPrefs }

  private val cloneAction =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == AppCompatActivity.RESULT_OK) {
        settings.edit { putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, true) }
        finish()
      }
    }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.cloneRemote.setOnClickListener { cloneToHiddenDir() }
    binding.createLocal.setOnClickListener { createRepository() }
  }

  /** Clones a remote Git repository to the app's private directory */
  private fun cloneToHiddenDir() {
    cloneAction.launch(GitServerConfigActivity.createCloneIntent(requireContext()))
  }

  private fun createRepository() {
    val localDir = PasswordRepository.getRepositoryDirectory()
    runCatching {
        if (localDir.notExists(LinkOption.NOFOLLOW_LINKS)) {
          localDir.createDirectories()
        }
        PasswordRepository.createRepository(localDir)
        if (!PasswordRepository.isInitialized) {
          PasswordRepository.initialize()
        }
        parentFragmentManager.performTransactionWithBackStack(KeySelectionFragment.newInstance())
      }
      .onFailure { e ->
        logcat(ERROR) { e.asLog() }
        if (!localDir.deleteIfExists()) {
          logcat { "Failed to delete local repository: $localDir" }
        }
        finish()
      }
  }

  companion object {

    fun newInstance(): CloneFragment = CloneFragment()
  }
}
