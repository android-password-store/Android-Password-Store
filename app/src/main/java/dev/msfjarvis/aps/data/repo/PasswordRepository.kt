/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.data.repo

import androidx.core.content.edit
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import dev.msfjarvis.aps.Application
import dev.msfjarvis.aps.data.password.PasswordItem
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.extensions.unsafeLazy
import dev.msfjarvis.aps.util.settings.PasswordSortOrder
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish

object PasswordRepository {

  var repository: Repository? = null
  private val settings by unsafeLazy { Application.instance.sharedPrefs }
  private val filesDir
    get() = Application.instance.filesDir
  val isInitialized: Boolean
    get() = repository != null

  fun isGitRepo(): Boolean {
    return repository?.objectDatabase?.exists() ?: false
  }

  /**
   * Takes in a [repositoryDir] to initialize a Git repository with, and assigns it to [repository]
   * as static state.
   */
  private fun initializeRepository(repositoryDir: File) {
    val builder = FileRepositoryBuilder()
    repository =
      runCatching { builder.setGitDir(repositoryDir).build() }.getOrElse { e ->
        e.printStackTrace()
        null
      }
  }

  fun createRepository(repositoryDir: File) {
    repositoryDir.delete()
    Git.init().setDirectory(repositoryDir).call()
    initializeRepository(repositoryDir)
  }

  // TODO add multiple remotes support for pull/push
  fun addRemote(name: String, url: String, replace: Boolean = false) {
    val storedConfig = repository!!.config
    val remotes = storedConfig.getSubsections("remote")

    if (!remotes.contains(name)) {
      runCatching {
        val uri = URIish(url)
        val refSpec = RefSpec("+refs/head/*:refs/remotes/$name/*")

        val remoteConfig = RemoteConfig(storedConfig, name)
        remoteConfig.addFetchRefSpec(refSpec)
        remoteConfig.addPushRefSpec(refSpec)
        remoteConfig.addURI(uri)
        remoteConfig.addPushURI(uri)

        remoteConfig.update(storedConfig)

        storedConfig.save()
      }
        .onFailure { e -> e.printStackTrace() }
    } else if (replace) {
      runCatching {
        val uri = URIish(url)

        val remoteConfig = RemoteConfig(storedConfig, name)
        // remove the first and eventually the only uri
        if (remoteConfig.urIs.size > 0) {
          remoteConfig.removeURI(remoteConfig.urIs[0])
        }
        if (remoteConfig.pushURIs.size > 0) {
          remoteConfig.removePushURI(remoteConfig.pushURIs[0])
        }

        remoteConfig.addURI(uri)
        remoteConfig.addPushURI(uri)

        remoteConfig.update(storedConfig)

        storedConfig.save()
      }
        .onFailure { e -> e.printStackTrace() }
    }
  }

  fun closeRepository() {
    repository?.close()
    repository = null
  }

  fun getRepositoryDirectory(): File {
    return if (settings.getBoolean(PreferenceKeys.GIT_EXTERNAL, false)) {
      val externalRepo = settings.getString(PreferenceKeys.GIT_EXTERNAL_REPO)
      if (externalRepo != null) File(externalRepo) else File(filesDir.toString(), "/store")
    } else {
      File(filesDir.toString(), "/store")
    }
  }

  fun initialize(): Repository? {
    val dir = getRepositoryDirectory()
    // Un-initialize the repo if the dir does not exist or is absolutely empty
    settings.edit {
      if (!dir.exists() || !dir.isDirectory || requireNotNull(dir.listFiles()).isEmpty()) {
        putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, false)
      } else {
        putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, true)
      }
    }
    // Create the repository static variable in PasswordRepository
    initializeRepository(dir.resolve(".git"))

    return repository
  }

  /**
   * Gets the .gpg files in a directory
   *
   * @param path the directory path
   * @return the list of gpg files in that directory
   */
  private fun getFilesList(path: File): ArrayList<File> {
    if (!path.exists()) return ArrayList()
    val files =
      (path.listFiles { file -> file.isDirectory || file.extension == "gpg" } ?: emptyArray())
        .toList()
    val items = ArrayList<File>()
    items.addAll(files)
    return items
  }

  /**
   * Gets the passwords (PasswordItem) in a directory
   *
   * @param path the directory path
   * @return a list of password items
   */
  fun getPasswords(
    path: File,
    rootDir: File,
    sortOrder: PasswordSortOrder
  ): ArrayList<PasswordItem> {
    // We need to recover the passwords then parse the files
    val passList = getFilesList(path).also { it.sortBy { f -> f.name } }
    val passwordList = ArrayList<PasswordItem>()
    val showHidden = settings.getBoolean(PreferenceKeys.SHOW_HIDDEN_CONTENTS, false)

    if (passList.size == 0) return passwordList
    if (!showHidden) {
      passList.filter { !it.isHidden }.toCollection(passList.apply { clear() })
    }
    passList.forEach { file ->
      passwordList.add(
        if (file.isFile) {
          PasswordItem.newPassword(file.name, file, rootDir)
        } else {
          PasswordItem.newCategory(file.name, file, rootDir)
        }
      )
    }
    passwordList.sortWith(sortOrder.comparator)
    return passwordList
  }
}
