/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.app.Application
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.recyclical.ViewHolderCreator
import com.github.ajalt.timberkt.i
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.yield
import me.zhanghai.android.fastscroll.PopupTextProvider

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
        when {
            filter[i].isWhitespace() -> i++
            filter[i].toLowerCase() == toMatch[j].toLowerCase() -> {
                i++
                bonusIncrement += 1
                bonus += bonusIncrement
                score += bonus
            }
            else -> {
                bonus = 0
                bonusIncrement = 0
            }
        }
        j++
    }
    return if (i == filter.length) score else 0
}

enum class FilterMode {
    ListOnly,
    StrictDomain,
    Fuzzy
}

enum class SearchMode {
    Recursive,
    CurrentDirectoryOnly
}

private data class SearchAction(
    val currentDir: File,
    val filter: String = "",
    val filterMode: FilterMode = FilterMode.ListOnly,
    val searchMode: SearchMode = SearchMode.CurrentDirectoryOnly,
    val listFilesOnly: Boolean = true
)

@ExperimentalCoroutinesApi
@FlowPreview
class SearchableRepositoryViewModel(application: Application) : AndroidViewModel(application) {
    private val root = PasswordRepository.getRepositoryDirectory(application)
    private val settings = PreferenceManager.getDefaultSharedPreferences(getApplication())
    private val sortOrder = PasswordRepository.PasswordSortOrder.getSortOrder(settings)
    private val showHiddenDirs = settings.getBoolean("show_hidden_folders", false)
    private val searchFromRoot = settings.getBoolean("search_from_root", false)
    private val defaultSearchMode = if (settings.getBoolean(
            "filter_recursively",
            true
        )
    ) SearchMode.Recursive else SearchMode.CurrentDirectoryOnly

    private val searchAction = MutableLiveData(SearchAction(root))
    private val searchActionFlow = searchAction.asFlow()
        .debounce(300)
        .distinctUntilChanged()

    private val passwordItemsFlow = searchActionFlow
        .mapLatest { searchAction ->
            val dirToSearch =
                if (searchFromRoot && searchAction.filterMode != FilterMode.ListOnly) root else searchAction.currentDir
            i { "Searching '$searchAction' in ${dirToSearch.absolutePath}" }
            val listResultFlow = when (searchAction.searchMode) {
                SearchMode.Recursive -> listFilesRecursively(dirToSearch)
                SearchMode.CurrentDirectoryOnly -> listFiles(dirToSearch)
            }
            val prefilteredResultFlow =
                if (searchAction.listFilesOnly) listResultFlow.filter { it.isFile } else listResultFlow
            when (searchAction.filterMode) {
                FilterMode.ListOnly -> {
                    prefilteredResultFlow.map { it.toPasswordItem(root) }.toList()
                        .sortedWith(sortOrder.comparator)
                }
                FilterMode.StrictDomain -> {
                    check(searchAction.listFilesOnly) { "Searches with StrictDomain search mode can only list files" }
                    prefilteredResultFlow.map { it.toPasswordItem(root) }.toList()
                }
                FilterMode.Fuzzy -> {
                    prefilteredResultFlow.map {
                        val item = it.toPasswordItem(root)
                        Pair(item.fuzzyMatch(searchAction.filter), item)
                    }
                    .filter { it.first > 0 }
                    .toList()
                    .sortedWith(
                        compareByDescending<Pair<Int, PasswordItem>> { it.first }.thenBy(
                            sortOrder.comparator
                        ) { it.second })
                    .map { it.second }
                }
            }
        }

    val passwordItemsList = passwordItemsFlow.asLiveData(Dispatchers.IO)

    fun list(currentDir: File) {
        require(currentDir.isDirectory) { "Can only list files in a directory" }
        searchAction.postValue(
            SearchAction(
                filter = "",
                currentDir = currentDir,
                filterMode = FilterMode.ListOnly,
                searchMode = SearchMode.CurrentDirectoryOnly,
                listFilesOnly = false
            )
        )
    }

    fun search(
        filter: String,
        currentDir: File? = null,
        filterMode: FilterMode = FilterMode.Fuzzy,
        searchMode: SearchMode? = null,
        listFilesOnly: Boolean = false
    ) {
        require(currentDir?.isDirectory != false) { "Can only search in a directory" }
        searchAction.postValue(
            SearchAction(
                filter = filter.trim(),
                currentDir = currentDir ?: searchAction.value!!.currentDir,
                filterMode = filterMode,
                searchMode = searchMode ?: defaultSearchMode,
                listFilesOnly = listFilesOnly
            )
        )
    }

    private fun shouldTake(file: File) = with(file) {
        if (isDirectory) {
            !isHidden || showHiddenDirs
        } else {
            !isHidden && file.extension == "gpg"
        }
    }

    private fun listFiles(dir: File): Flow<File> {
        return dir.listFiles { file -> shouldTake(file) }?.asFlow() ?: emptyFlow()
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

private object PasswordItemDiffCallback : DiffUtil.ItemCallback<PasswordItem>() {
    override fun areItemsTheSame(oldItem: PasswordItem, newItem: PasswordItem) =
        oldItem.file.absolutePath == newItem.file.absolutePath

    override fun areContentsTheSame(oldItem: PasswordItem, newItem: PasswordItem) = oldItem == newItem
}

abstract class PasswordItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    abstract fun bind(item: PasswordItem)
}

class DelegatedSearchableRepositoryAdapter<T : PasswordItemViewHolder>(
    private val layoutRes: Int,
    private val viewHolderCreator: ViewHolderCreator<T>
) : ListAdapter<PasswordItem, T>(PasswordItemDiffCallback), PopupTextProvider {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): T {
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutRes, parent, false)
        return viewHolderCreator(view)
    }

    override fun onBindViewHolder(holder: T, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getPopupText(position: Int): String {
        return getItem(position).file.name[0].toString().toUpperCase(Locale.getDefault())
    }
}
