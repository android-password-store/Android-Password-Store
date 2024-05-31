/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.data.repo

import androidx.core.content.edit
import app.passwordstore.Application
import app.passwordstore.util.extensions.sharedPrefs
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.settings.PreferenceKeys
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.PathWalkOption
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.walk
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish

@OptIn(ExperimentalPathApi::class)
object PasswordRepository {

  var repository: Repository? = null
  private val settings by unsafeLazy { Application.instance.sharedPrefs }
  private val filesDir
    get() = Application.instance.filesDir.toPath()

  val isInitialized: Boolean
    get() = repository != null

  fun isGitRepo(): Boolean {
    return repository?.objectDatabase?.exists() ?: false
  }

  /**
   * Takes in a [repositoryDir] to initialize a Git repository with, and assigns it to [repository]
   * as static state.
   */
  private fun initializeRepository(repositoryDir: Path) {
    val builder = FileRepositoryBuilder()
    repository =
      runCatching { builder.setGitDir(repositoryDir.toFile()).build() }
        .getOrElse { e ->
          e.printStackTrace()
          null
        }
  }

  @OptIn(ExperimentalPathApi::class)
  fun createRepository(repositoryDir: Path) {
    repositoryDir.deleteRecursively()
    repository = Git.init().setDirectory(repositoryDir.toFile()).call().repository
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

  fun getRepositoryDirectory(): Path {
    return filesDir.resolve("store")
  }

  fun initialize(): Repository? {
    val dir = getRepositoryDirectory()
    // Un-initialize the repo if the dir does not exist or is absolutely empty
    settings.edit {
      if (
        !dir.exists() ||
          !dir.isDirectory() ||
          dir.walk(PathWalkOption.INCLUDE_DIRECTORIES).toList().isEmpty()
      ) {
        putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, false)
      } else {
        putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, true)
      }
    }
    // Create the repository static variable in PasswordRepository
    initializeRepository(dir.resolve(".git"))

    return repository
  }

  /** Get the currently checked out branch. */
  fun getCurrentBranch(): String? {
    val repository = repository ?: return null
    val headRef = repository.findRef(Constants.HEAD) ?: return null
    return if (headRef.isSymbolic) {
      val branchName = headRef.target.name
      Repository.shortenRefName(branchName)
    } else {
      null
    }
  }
}
