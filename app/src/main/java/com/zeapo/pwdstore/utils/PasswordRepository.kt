/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.zeapo.pwdstore.Application
import java.io.File
import java.io.FileFilter
import java.util.Comparator
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.URIish

open class PasswordRepository protected constructor() {

    enum class PasswordSortOrder(val comparator: Comparator<PasswordItem>) {

        FOLDER_FIRST(Comparator { p1: PasswordItem, p2: PasswordItem ->
            (p1.type + p1.name)
                .compareTo(p2.type + p2.name, ignoreCase = true)
        }),

        INDEPENDENT(Comparator { p1: PasswordItem, p2: PasswordItem ->
            p1.name.compareTo(p2.name, ignoreCase = true)
        }),

        RECENTLY_USED(Comparator { p1: PasswordItem, p2: PasswordItem ->
            val recentHistory = Application.instance.getSharedPreferences("recent_password_history", Context.MODE_PRIVATE)
            val timeP1 = recentHistory.getString(p1.file.absolutePath.base64())
            val timeP2 = recentHistory.getString(p2.file.absolutePath.base64())
            when {
                timeP1 != null && timeP2 != null -> timeP2.compareTo(timeP1)
                timeP1 != null && timeP2 == null -> return@Comparator -1
                timeP1 == null && timeP2 != null -> return@Comparator 1
                else -> p1.name.compareTo(p2.name, ignoreCase = true)
            }
        }),

        FILE_FIRST(Comparator { p1: PasswordItem, p2: PasswordItem ->
            (p2.type + p1.name).compareTo(p1.type + p2.name, ignoreCase = true)
        });

        companion object {

            @JvmStatic
            fun getSortOrder(settings: SharedPreferences): PasswordSortOrder {
                return valueOf(settings.getString(PreferenceKeys.SORT_ORDER) ?: FOLDER_FIRST.name)
            }
        }
    }

    companion object {

        private var repository: Repository? = null
        private val settings by lazy { Application.instance.sharedPrefs }
        private val filesDir
            get() = Application.instance.filesDir

        /**
         * Returns the git repository
         *
         * @param localDir needed only on the creation
         * @return the git repository
         */
        @JvmStatic
        fun getRepository(localDir: File?): Repository? {
            if (repository == null && localDir != null) {
                val builder = FileRepositoryBuilder()
                try {
                    repository = builder.setGitDir(localDir)
                        .readEnvironment()
                        .build()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return repository
        }

        @JvmStatic
        val isInitialized: Boolean
            get() = repository != null

        @JvmStatic
        fun isGitRepo(): Boolean {
            if (repository != null) {
                return repository!!.objectDatabase.exists()
            }
            return false
        }

        @JvmStatic
        @Throws(Exception::class)
        fun createRepository(localDir: File) {
            localDir.delete()

            Git.init().setDirectory(localDir).call()
            getRepository(localDir)
        }

        // TODO add multiple remotes support for pull/push
        @JvmStatic
        fun addRemote(name: String, url: String, replace: Boolean = false) {
            val storedConfig = repository!!.config
            val remotes = storedConfig.getSubsections("remote")

            if (!remotes.contains(name)) {
                try {
                    val uri = URIish(url)
                    val refSpec = RefSpec("+refs/head/*:refs/remotes/$name/*")

                    val remoteConfig = RemoteConfig(storedConfig, name)
                    remoteConfig.addFetchRefSpec(refSpec)
                    remoteConfig.addPushRefSpec(refSpec)
                    remoteConfig.addURI(uri)
                    remoteConfig.addPushURI(uri)

                    remoteConfig.update(storedConfig)

                    storedConfig.save()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (replace) {
                try {
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        @JvmStatic
        fun closeRepository() {
            if (repository != null) repository!!.close()
            repository = null
        }

        @JvmStatic
        fun getRepositoryDirectory(): File {
            return if (settings.getBoolean(PreferenceKeys.GIT_EXTERNAL, false)) {
                val externalRepo = settings.getString(PreferenceKeys.GIT_EXTERNAL_REPO)
                if (externalRepo != null)
                    File(externalRepo)
                else
                    File(filesDir.toString(), "/store")
            } else {
                File(filesDir.toString(), "/store")
            }
        }

        @JvmStatic
        fun initialize(): Repository? {
            val dir = getRepositoryDirectory()
            // uninitialize the repo if the dir does not exist or is absolutely empty
            settings.edit {
                if (!dir.exists() || !dir.isDirectory || dir.listFiles()?.isEmpty() == true) {
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
        @JvmStatic
        fun getFilesList(path: File?): ArrayList<File> {
            if (path == null || !path.exists()) return ArrayList()

            val directories = (path.listFiles(FileFilter { pathname -> pathname.isDirectory })
                ?: emptyArray()).toList()
            val files = (path.listFiles(FileFilter { pathname -> pathname.extension == "gpg" })
                ?: emptyArray()).toList()

            val items = ArrayList<File>()
            items.addAll(directories)
            items.addAll(files)

            return items
        }

        /**
         * Gets the passwords (PasswordItem) in a directory
         *
         * @param path the directory path
         * @return a list of password items
         */
        @JvmStatic
        fun getPasswords(path: File, rootDir: File, sortOrder: PasswordSortOrder): ArrayList<PasswordItem> {
            // We need to recover the passwords then parse the files
            val passList = getFilesList(path).also { it.sortBy { f -> f.name } }
            val passwordList = ArrayList<PasswordItem>()
            val showHidden = settings.getBoolean(PreferenceKeys.SHOW_HIDDEN_CONTENTS, false)

            if (passList.size == 0) return passwordList
            if (!showHidden) {
                passList.filter { !it.isHidden }.toCollection(passList.apply { clear() })
            }
            passList.forEach { file ->
                passwordList.add(if (file.isFile) {
                    PasswordItem.newPassword(file.name, file, rootDir)
                } else {
                    PasswordItem.newCategory(file.name, file, rootDir)
                })
            }
            passwordList.sortWith(sortOrder.comparator)
            return passwordList
        }
    }
}
