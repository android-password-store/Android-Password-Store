/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.data.repo

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import dev.msfjarvis.aps.Application
import dev.msfjarvis.aps.data.password.PasswordItem
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.settings.PasswordSortOrder
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.FS_POSIX_Java6

object PasswordRepository {

  @RequiresApi(Build.VERSION_CODES.O)
  private class FS_POSIX_Java6_with_optional_symlinks : FS_POSIX_Java6() {

    override fun supportsSymlinks() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    override fun isSymLink(file: File) = Files.isSymbolicLink(file.toPath())

    override fun readSymLink(file: File) = Files.readSymbolicLink(file.toPath()).toString()

    override fun createSymLink(source: File, target: String) {
      val sourcePath = source.toPath()
      if (Files.exists(sourcePath, LinkOption.NOFOLLOW_LINKS)) Files.delete(sourcePath)
      Files.createSymbolicLink(sourcePath, File(target).toPath())
    }
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private class Java7FSFactory : FS.FSFactory() {

    override fun detect(cygwinUsed: Boolean?): FS {
      return FS_POSIX_Java6_with_optional_symlinks()
    }
  }

  private var repository: Repository? = null
  private val settings by lazy(LazyThreadSafetyMode.NONE) { Application.instance.sharedPrefs }
  private val filesDir
    get() = Application.instance.filesDir

  /**
   * Returns the git repository
   *
   * @param localDir needed only on the creation
   * @return the git repository
   */
  fun getRepository(localDir: File?): Repository? {
    if (repository == null && localDir != null) {
      val builder = FileRepositoryBuilder()
      repository =
        runCatching {
          builder
            .run {
              gitDir = localDir
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fs = Java7FSFactory().detect(null)
              }
              readEnvironment()
            }
            .build()
        }
          .getOrElse { e ->
            e.printStackTrace()
            null
          }
    }
    return repository
  }

  val isInitialized: Boolean
    get() = repository != null

  fun isGitRepo(): Boolean {
    return repository?.objectDatabase?.exists() ?: false
  }

  fun createRepository(localDir: File) {
    localDir.delete()

    Git.init().setDirectory(localDir).call()
    getRepository(localDir)
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
    // uninitialize the repo if the dir does not exist or is absolutely empty
    settings.edit {
      if (!dir.exists() || !dir.isDirectory || requireNotNull(dir.listFiles()).isEmpty()) {
        putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, false)
      } else {
        putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, true)
      }
    }

    // create the repository static variable in PasswordRepository
    return getRepository(File(dir.absolutePath + "/.git"))
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
