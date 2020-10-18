/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 *
 */

package com.zeapo.pwdstore.ui.onboarding.fragments

import android.Manifest
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
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.databinding.FragmentRepoLocationBinding
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PasswordSortOrder
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.finish
import com.zeapo.pwdstore.utils.getString
import com.zeapo.pwdstore.utils.isPermissionGranted
import com.zeapo.pwdstore.utils.listFilesRecursively
import com.zeapo.pwdstore.utils.performTransactionWithBackStack
import com.zeapo.pwdstore.utils.sharedPrefs
import com.zeapo.pwdstore.utils.viewBinding
import java.io.File

class RepoLocationFragment : Fragment(R.layout.fragment_repo_location) {

    private val settings by lazy(LazyThreadSafetyMode.NONE) { requireActivity().applicationContext.sharedPrefs }
    private val binding by viewBinding(FragmentRepoLocationBinding::bind)
    private val sortOrder: PasswordSortOrder
        get() = PasswordSortOrder.getSortOrder(settings)

    private val repositoryInitAction = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            initializeRepositoryInfo()
        }
    }

    private val externalDirectorySelectAction = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            if (checkExternalDirectory()) {
                finish()
            } else {
                createRepository()
            }
        }
    }

    private val externalDirPermGrantedAction = createPermGrantedAction {
        externalDirectorySelectAction.launch(UserPreference.createDirectorySelectionIntent(requireContext()))
    }

    private val repositoryUsePermGrantedAction = createPermGrantedAction {
        initializeRepositoryInfo()
    }

    private val repositoryChangePermGrantedAction = createPermGrantedAction {
        repositoryInitAction.launch(UserPreference.createDirectorySelectionIntent(requireContext()))
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
            if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                externalDirPermGrantedAction.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                // Unlikely we have storage permissions without user ever selecting a directory,
                // but let's not assume.
                externalDirectorySelectAction.launch(UserPreference.createDirectorySelectionIntent(requireContext()))
            }
        } else {
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle(resources.getString(R.string.directory_selected_title))
                .setMessage(resources.getString(R.string.directory_selected_message, externalRepo))
                .setPositiveButton(resources.getString(R.string.use)) { _, _ ->
                    if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        repositoryUsePermGrantedAction.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        initializeRepositoryInfo()
                    }
                }
                .setNegativeButton(resources.getString(R.string.change)) { _, _ ->
                    if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        repositoryChangePermGrantedAction.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        repositoryInitAction.launch(UserPreference.createDirectorySelectionIntent(requireContext()))
                    }
                }
                .show()
        }
    }

    private fun checkExternalDirectory(): Boolean {
        if (settings.getBoolean(PreferenceKeys.GIT_EXTERNAL, false) &&
            settings.getString(PreferenceKeys.GIT_EXTERNAL_REPO) != null) {
            val externalRepoPath = settings.getString(PreferenceKeys.GIT_EXTERNAL_REPO)
            val dir = externalRepoPath?.let { File(it) }
            if (dir != null && // The directory could be opened
                dir.exists() && // The directory exists
                dir.isDirectory && // The directory, is really a directory
                dir.listFilesRecursively().isNotEmpty() && // The directory contains files
                // The directory contains a non-zero number of password files
                PasswordRepository.getPasswords(dir, PasswordRepository.getRepositoryDirectory(), sortOrder).isNotEmpty()
            ) {
                PasswordRepository.closeRepository()
                return true
            }
        }
        return false
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
        if (externalRepo && !isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return
        }
        if (externalRepo && externalRepoPath != null) {
            if (checkExternalDirectory()) {
                finish()
                return
            }
        }
        createRepository()
    }

    private fun createPermGrantedAction(block: () -> Unit) =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                block.invoke()
            }
        }

    companion object {

        fun newInstance(): RepoLocationFragment = RepoLocationFragment()
    }
}
