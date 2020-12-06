/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.ui.onboarding.fragments

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.ui.settings.UserPreference
import dev.msfjarvis.aps.databinding.FragmentRepoLocationBinding
import dev.msfjarvis.aps.data.repo.PasswordRepository
import dev.msfjarvis.aps.injection.Graph
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import dev.msfjarvis.aps.util.extensions.finish
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.performTransactionWithBackStack
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.extensions.viewBinding

class RepoLocationFragment : Fragment(R.layout.fragment_repo_location) {

    private val settings by lazy(LazyThreadSafetyMode.NONE) { requireActivity().applicationContext.sharedPrefs }
    private val binding by viewBinding(FragmentRepoLocationBinding::bind)

    private val repositoryInitAction = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            initializeRepositoryInfo()
        }
    }

    private val externalDirectorySelectAction = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            Graph.buildStoreImpl(requireContext())
            finish()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.hidden.setOnClickListener {
            createRepoInHiddenDir()
        }

        binding.sdcard.setOnClickListener {
            createRepoFromExternalDir()
        }
    }

    /**
     * Initializes an empty repository in the app's private directory
     */
    private fun createRepoInHiddenDir() {
        settings.edit {
            putBoolean(PreferenceKeys.GIT_EXTERNAL, false)
            remove(PreferenceKeys.GIT_EXTERNAL_REPO)
        }
        initializeRepositoryInfo()
    }

    /**
     * Initializes an empty repository in a selected directory if one does not already exist
     */
    private fun createRepoFromExternalDir() {
        settings.edit { putBoolean(PreferenceKeys.GIT_EXTERNAL, true) }
        val externalRepo = settings.getString(PreferenceKeys.GIT_EXTERNAL_REPO)
        if (externalRepo == null) {
            externalDirectorySelectAction.launch(UserPreference.createDirectorySelectionIntent(requireContext()))
        } else {
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle(resources.getString(R.string.directory_selected_title))
                .setMessage(resources.getString(R.string.directory_selected_message, externalRepo))
                .setPositiveButton(resources.getString(R.string.use)) { _, _ ->
                    initializeRepositoryInfo()
                }
                .setNegativeButton(resources.getString(R.string.change)) { _, _ ->
                    repositoryInitAction.launch(UserPreference.createDirectorySelectionIntent(requireContext()))
                }
                .show()
        }
    }

    private fun createRepository() {
        val localDir = PasswordRepository.getRepositoryDirectory()
        runCatching {
            check(localDir.exists() || localDir.mkdir()) { "Failed to create directory!" }
            PasswordRepository.createRepository(localDir)
            if (!PasswordRepository.isInitialized) {
                PasswordRepository.initialize()
            }
            parentFragmentManager.performTransactionWithBackStack(KeySelectionFragment.newInstance())
        }.onFailure { e ->
            e(e)
            if (!localDir.delete()) {
                d { "Failed to delete local repository: $localDir" }
            }
            finish()
        }
    }

    private fun initializeRepositoryInfo() {
        val externalRepo = settings.getBoolean(PreferenceKeys.GIT_EXTERNAL, false)
        val externalRepoPath = settings.getString(PreferenceKeys.GIT_EXTERNAL_REPO)
        if (externalRepo && externalRepoPath != null) {
            finish()
            return
        }
        createRepository()
    }

    companion object {

        fun newInstance(): RepoLocationFragment = RepoLocationFragment()
    }
}
