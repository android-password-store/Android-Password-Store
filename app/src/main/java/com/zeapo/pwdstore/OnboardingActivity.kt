/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.github.ajalt.timberkt.d
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.databinding.ActivityOnboardingBinding
import com.zeapo.pwdstore.git.BaseGitActivity
import com.zeapo.pwdstore.git.GitServerConfigActivity
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.isPermissionGranted
import com.zeapo.pwdstore.utils.getString
import com.zeapo.pwdstore.utils.listFilesRecursively
import com.zeapo.pwdstore.utils.sharedPrefs
import com.zeapo.pwdstore.utils.viewBinding
import java.io.File

class OnboardingActivity : AppCompatActivity() {

    private val binding by viewBinding(ActivityOnboardingBinding::inflate)
    private val settings by lazy { applicationContext.sharedPrefs }
    private val sortOrder: PasswordRepository.PasswordSortOrder
        get() = PasswordRepository.PasswordSortOrder.getSortOrder(settings)

    private val cloneAction = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            settings.edit { putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, true) }
            finish()
        }
    }

    private val repositoryInitAction = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            initializeRepositoryInfo()
            finish()
        }
    }

    private val externalDirectorySelectAction = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            if (checkExternalDirectory()) {
                finish()
            } else {
                createRepository()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(binding.root)
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, UserPreference::class.java))
        }
        binding.localDirectoryButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.location_dialog_title))
                .setMessage(resources.getString(R.string.location_dialog_create_text))
                .setPositiveButton(resources.getString(R.string.location_hidden)) { _, _ ->
                    createRepoInHiddenDir()
                }
                .setNegativeButton(resources.getString(R.string.location_sdcard)) { _, _ ->
                    createRepoFromExternalDir()
                }
                .show()
        }
        binding.cloneFromServerButton.setOnClickListener {
            cloneToHiddenDir()
        }
    }

    override fun onBackPressed() {
        finishAffinity()
    }

    /**
     * Clones a remote Git repository to the app's private directory
     */
    private fun cloneToHiddenDir() {
        settings.edit { putBoolean(PreferenceKeys.GIT_EXTERNAL, false) }
        cloneAction.launch(Intent(this, GitServerConfigActivity::class.java).apply {
            putExtra(BaseGitActivity.REQUEST_ARG_OP, BaseGitActivity.REQUEST_CLONE)
        })
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
        finish()
    }

    /**
     * Initializes an empty repository in a selected directory if one does not already exist
     */
    private fun createRepoFromExternalDir() {
        settings.edit { putBoolean(PreferenceKeys.GIT_EXTERNAL, true) }
        val externalRepo = settings.getString(PreferenceKeys.GIT_EXTERNAL_REPO)
        if (externalRepo == null) {
            if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                registerForActivityResult(RequestPermission()) { granted ->
                    if (granted) {
                        externalDirectorySelectAction.launch(UserPreference.createDirectorySelectionIntent(this))
                    }
                }.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.directory_selected_title))
                .setMessage(resources.getString(R.string.directory_selected_message, externalRepo))
                .setPositiveButton(resources.getString(R.string.use)) { _, _ ->
                    if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        registerForActivityResult(RequestPermission()) { granted ->
                            if (granted) {
                                initializeRepositoryInfo()
                            }
                        }.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        initializeRepositoryInfo()
                    }
                }
                .setNegativeButton(resources.getString(R.string.change)) { _, _ ->
                    if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        registerForActivityResult(RequestPermission()) { granted ->
                            if (granted) {
                                repositoryInitAction.launch(UserPreference.createDirectorySelectionIntent(this))
                            }
                        }.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        repositoryInitAction.launch(UserPreference.createDirectorySelectionIntent(this))
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
            if (File(localDir.absolutePath + "/.gpg-id").createNewFile()) {
                settings.edit { putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, true) }
            } else {
                throw IllegalStateException("Failed to initialize repository state.")
            }
        }.onFailure { e ->
            e.printStackTrace()
            if (!localDir.delete()) {
                d { "Failed to delete local repository: $localDir" }
            }
        }
    }

    private fun initializeRepositoryInfo() {
        val externalRepo = settings.getBoolean(PreferenceKeys.GIT_EXTERNAL, false)
        val externalRepoPath = settings.getString(PreferenceKeys.GIT_EXTERNAL_REPO)
        if (externalRepo && !isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            return
        }
        if (externalRepo && externalRepoPath != null) {
            if (checkExternalDirectory()) return
        }
        createRepository()
    }
}
