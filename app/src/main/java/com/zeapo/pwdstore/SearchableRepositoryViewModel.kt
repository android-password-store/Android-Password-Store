/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import com.github.ajalt.timberkt.i
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.yield

private fun File.toPasswordItem(root: File) = if (isFile)
    PasswordItem.newPassword(name, this, root)
else
    PasswordItem.newCategory(name, this, root)

private fun PasswordItem.fuzzyMatch(filter: String): Int {
    var i = 0
    var j = 0
    var score = 0
    var bonus = 0
    var bonusIncrement = 0

    val toMatch = longName

    while (i < filter.length && j < toMatch.length) {
        if (filter[i].isWhitespace()) {
            i++
        } else if (filter[i].toLowerCase() == toMatch[j].toLowerCase()) {
            i++
            bonusIncrement += 1
            bonus += bonusIncrement
            score += bonus
        } else {
            bonus = 0
            bonusIncrement = 0
        }
        j++
    }
    return if (i == filter.length) score else 0
}

@ExperimentalCoroutinesApi
@FlowPreview
class SearchableRepositoryViewModel(application: Application) : AndroidViewModel(application) {
    private val root = PasswordRepository.getRepositoryDirectory(application)
    private val settings = PreferenceManager.getDefaultSharedPreferences(getApplication())
    private val sortOrder = PasswordRepository.PasswordSortOrder.getSortOrder(settings)
    private val showHiddenDirs = settings.getBoolean("show_hidden_folders", false)
    private val searchFromRoot = settings.getBoolean("search_from_root", false)

    private val searchFilter = MutableLiveData("")
    private val searchFilterFlow = searchFilter.asFlow()
        .debounce(300)
        .map { it.trim() }

    private val currentDir = MutableLiveData<File>()
    private val currentDirFlow = currentDir.asFlow()

    private val searchActionFlow = searchFilterFlow
        .combine(currentDirFlow) { filter, dir -> Pair(filter, dir) }
        .distinctUntilChanged()
    private val passwordItemsFlow = searchActionFlow
        .mapLatest { (filter, dir) ->
            i { "Searching '$filter' in ${dir.absolutePath}" }
            if (filter.isNotEmpty()) {
                // Search directory contents recursively
                val dirToSearch = if (searchFromRoot) root else dir
                listFilesRecursively(dirToSearch)
                    .map {
                        val item = it.toPasswordItem(root)
                        Pair(item.fuzzyMatch(filter), item)
                    }
                    .filter { it.first > 0 }
                    .toList()
                    .sortedWith(
                        compareByDescending<Pair<Int, PasswordItem>> { it.first }.thenBy(
                            sortOrder.comparator
                        ) { it.second })
                    .map { it.second }
            } else {
                // List directory contents non-recursively
                listFiles(dir)
                    .map { it.toPasswordItem(root) }
                    .toList()
                    .sortedWith(sortOrder.comparator)
            }
        }

    val passwordItemsList = passwordItemsFlow.asLiveData(Dispatchers.IO)

    fun navigateTo(file: File) {
        require(file.isDirectory) { "Cannot navigate to a file" }
        currentDir.postValue(file)
    }

    fun search(filter: String) {
        searchFilter.postValue(filter)
    }

    private fun shouldTake(file: File) = with(file) {
        if (isDirectory) {
            !isHidden || showHiddenDirs
        } else {
            !isHidden && file.extension == "gpg"
        }
    }

    private fun listFiles(dir: File): Sequence<File> {
        return dir.listFiles { file -> shouldTake(file) }?.asSequence() ?: emptySequence()
    }

    private fun listFilesRecursively(dir: File): Flow<File> {
        return dir
            .walkTopDown().onEnter { file -> shouldTake(file) }
            .asFlow()
            .map {
                // Makes the flow cancellable (to verify, replace next line with `delay(100)`)
                // TODO: Measure performance impact
                yield()
                it
            }
            .filter { file -> shouldTake(file) }
    }
}

object PasswordItemDiffCallback : DiffUtil.ItemCallback<PasswordItem>() {
    override fun areItemsTheSame(oldItem: PasswordItem, newItem: PasswordItem) =
        oldItem.file.absolutePath == newItem.file.absolutePath

    override fun areContentsTheSame(oldItem: PasswordItem, newItem: PasswordItem) = oldItem == newItem
}
