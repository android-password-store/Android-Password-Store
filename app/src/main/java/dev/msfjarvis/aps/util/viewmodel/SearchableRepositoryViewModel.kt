/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.viewmodel

import android.app.Application
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.Selection
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.msfjarvis.aps.data.password.PasswordItem
import dev.msfjarvis.aps.data.repo.PasswordRepository
import dev.msfjarvis.aps.util.autofill.AutofillPreferences
import dev.msfjarvis.aps.util.autofill.DirectoryStructure
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.extensions.unsafeLazy
import dev.msfjarvis.aps.util.settings.PasswordSortOrder
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import dev.sphericalkat.sublimefuzzy.Fuzzy
import java.io.File
import java.text.Collator
import java.util.Locale
import java.util.Stack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import me.zhanghai.android.fastscroll.PopupTextProvider

private fun File.toPasswordItem() =
  if (isFile) PasswordItem.newPassword(name, this, PasswordRepository.getRepositoryDirectory())
  else PasswordItem.newCategory(name, this, PasswordRepository.getRepositoryDirectory())

private fun PasswordItem.fuzzyMatch(filter: String): Int {
  val (_, score) = Fuzzy.fuzzyMatch(filter, longName)
  return score
}

private val CaseInsensitiveComparator = Collator.getInstance().apply { strength = Collator.PRIMARY }

private fun PasswordItem.Companion.makeComparator(
  typeSortOrder: PasswordSortOrder,
  directoryStructure: DirectoryStructure
): Comparator<PasswordItem> {
  return when (typeSortOrder) {
      PasswordSortOrder.FOLDER_FIRST -> compareBy { it.type }
      // In order to let INDEPENDENT not distinguish between items based on their type, we
      // simply
      // declare them all equal at this stage.
      PasswordSortOrder.INDEPENDENT -> Comparator { _, _ -> 0 }
      PasswordSortOrder.FILE_FIRST -> compareByDescending { it.type }
      PasswordSortOrder.RECENTLY_USED -> PasswordSortOrder.RECENTLY_USED.comparator
    }
    .then(
      compareBy(nullsLast(CaseInsensitiveComparator)) {
        directoryStructure.getIdentifierFor(it.file)
      }
    )
    .then(
      compareBy(nullsLast(CaseInsensitiveComparator)) { directoryStructure.getUsernameFor(it.file) }
    )
}

val PasswordItem.stableId: String
  get() = file.absolutePath

enum class FilterMode {
  NoFilter,
  StrictDomain,
  Fuzzy
}

enum class SearchMode {
  RecursivelyInSubdirectories,
  InCurrentDirectoryOnly
}

enum class ListMode {
  FilesOnly,
  DirectoriesOnly,
  AllEntries
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchableRepositoryViewModel(application: Application) : AndroidViewModel(application) {

  private var _updateCounter = 0
  private val updateCounter: Int
    get() = _updateCounter

  private fun forceUpdateOnNextSearchAction() {
    _updateCounter++
  }

  private val root
    get() = PasswordRepository.getRepositoryDirectory()
  private val settings by unsafeLazy { application.sharedPrefs }
  private val showHiddenContents
    get() = settings.getBoolean(PreferenceKeys.SHOW_HIDDEN_CONTENTS, false)
  private val defaultSearchMode
    get() =
      if (settings.getBoolean(PreferenceKeys.FILTER_RECURSIVELY, true)) {
        SearchMode.RecursivelyInSubdirectories
      } else {
        SearchMode.InCurrentDirectoryOnly
      }

  private val typeSortOrder
    get() = PasswordSortOrder.getSortOrder(settings)
  private val directoryStructure
    get() = AutofillPreferences.directoryStructure(getApplication())
  private val itemComparator
    get() = PasswordItem.makeComparator(typeSortOrder, directoryStructure)

  private data class SearchAction(
    val baseDirectory: File,
    val filter: String,
    val filterMode: FilterMode,
    val searchMode: SearchMode,
    val listMode: ListMode,
    // This counter can be increased to force a reexecution of the search action even if all
    // other arguments are left unchanged.
    val updateCounter: Int
  )

  private fun makeSearchAction(
    baseDirectory: File,
    filter: String,
    filterMode: FilterMode,
    searchMode: SearchMode,
    listMode: ListMode
  ): SearchAction {
    return SearchAction(
      baseDirectory = baseDirectory,
      filter = filter,
      filterMode = filterMode,
      searchMode = searchMode,
      listMode = listMode,
      updateCounter = updateCounter
    )
  }

  private fun updateSearchAction(action: SearchAction) = action.copy(updateCounter = updateCounter)

  private val searchAction =
    MutableLiveData(
      makeSearchAction(
        baseDirectory = root,
        filter = "",
        filterMode = FilterMode.NoFilter,
        searchMode = SearchMode.InCurrentDirectoryOnly,
        listMode = ListMode.AllEntries
      )
    )
  private val searchActionFlow = searchAction.asFlow().distinctUntilChanged()

  data class SearchResult(val passwordItems: List<PasswordItem>, val isFiltered: Boolean)

  val searchResult =
    searchActionFlow
      .mapLatest { searchAction ->
        val listResultFlow =
          when (searchAction.searchMode) {
            SearchMode.RecursivelyInSubdirectories ->
              listFilesRecursively(searchAction.baseDirectory)
            SearchMode.InCurrentDirectoryOnly -> listFiles(searchAction.baseDirectory)
          }
        val prefilteredResultFlow =
          when (searchAction.listMode) {
            ListMode.FilesOnly -> listResultFlow.filter { it.isFile }
            ListMode.DirectoriesOnly -> listResultFlow.filter { it.isDirectory }
            ListMode.AllEntries -> listResultFlow
          }
        val passwordList =
          when (if (searchAction.filter == "") FilterMode.NoFilter else searchAction.filterMode) {
            FilterMode.NoFilter -> {
              prefilteredResultFlow.map { it.toPasswordItem() }.toList().sortedWith(itemComparator)
            }
            FilterMode.StrictDomain -> {
              check(searchAction.listMode == ListMode.FilesOnly) {
                "Searches with StrictDomain search mode can only list files"
              }
              val regex = generateStrictDomainRegex(searchAction.filter)
              if (regex != null) {
                prefilteredResultFlow
                  .filter { absoluteFile ->
                    regex.containsMatchIn(absoluteFile.relativeTo(root).path)
                  }
                  .map { it.toPasswordItem() }
                  .toList()
                  .sortedWith(itemComparator)
              } else {
                emptyList()
              }
            }
            FilterMode.Fuzzy -> {
              prefilteredResultFlow
                .map {
                  val item = it.toPasswordItem()
                  Pair(item.fuzzyMatch(searchAction.filter), item)
                }
                .filter { it.first > 0 }
                .toList()
                .sortedWith(
                  compareByDescending<Pair<Int, PasswordItem>> { it.first }.thenBy(itemComparator) {
                    it.second
                  }
                )
                .map { it.second }
            }
          }
        SearchResult(passwordList, isFiltered = searchAction.filterMode != FilterMode.NoFilter)
      }
      .asLiveData(Dispatchers.IO)

  private fun shouldTake(file: File) =
    with(file) {
      if (showHiddenContents) return true
      if (isDirectory) {
        !isHidden
      } else {
        !isHidden && file.extension == "gpg"
      }
    }

  private fun listFiles(dir: File): Flow<File> {
    return dir.listFiles { file -> shouldTake(file) }?.asFlow() ?: emptyFlow()
  }

  private fun listFilesRecursively(dir: File): Flow<File> {
    return dir
      // Take top directory even if it is hidden.
      .walkTopDown()
      .onEnter { file -> file == dir || shouldTake(file) }
      .asFlow()
      // Skip the root directory
      .drop(1)
      .map {
        yield()
        it
      }
      .filter { file -> shouldTake(file) }
  }

  private val _currentDir = MutableLiveData(root)
  val currentDir = _currentDir as LiveData<File>

  data class NavigationStackEntry(val dir: File, val recyclerViewState: Parcelable?)

  private val navigationStack = Stack<NavigationStackEntry>()

  fun navigateTo(
    newDirectory: File = root,
    listMode: ListMode = ListMode.AllEntries,
    recyclerViewState: Parcelable? = null,
    pushPreviousLocation: Boolean = true
  ) {
    if (!newDirectory.exists()) return
    require(newDirectory.isDirectory) { "Can only navigate to a directory" }
    if (pushPreviousLocation) {
      navigationStack.push(NavigationStackEntry(_currentDir.value!!, recyclerViewState))
    }
    searchAction.postValue(
      makeSearchAction(
        filter = "",
        baseDirectory = newDirectory,
        filterMode = FilterMode.NoFilter,
        searchMode = SearchMode.InCurrentDirectoryOnly,
        listMode = listMode
      )
    )
    _currentDir.postValue(newDirectory)
  }

  val canNavigateBack
    get() = navigationStack.isNotEmpty()

  /**
   * Navigate back to the last location on the [navigationStack] and restore a cached scroll
   * position if possible.
   *
   * Returns the old RecyclerView's LinearLayoutManager state as a [Parcelable] if it was cached.
   */
  fun navigateBack(): Parcelable? {
    if (!canNavigateBack) return null
    val (oldDir, oldRecyclerViewState) = navigationStack.pop()
    navigateTo(oldDir, pushPreviousLocation = false)
    return oldRecyclerViewState
  }

  fun reset() {
    navigationStack.clear()
    forceUpdateOnNextSearchAction()
    navigateTo(pushPreviousLocation = false)
  }

  fun search(
    filter: String,
    baseDirectory: File? = null,
    filterMode: FilterMode = FilterMode.Fuzzy,
    searchMode: SearchMode? = null,
    listMode: ListMode = ListMode.AllEntries
  ) {
    require(baseDirectory?.isDirectory != false) { "Can only search in a directory" }
    searchAction.postValue(
      makeSearchAction(
        filter = filter,
        baseDirectory = baseDirectory ?: _currentDir.value!!,
        filterMode = filterMode,
        searchMode = searchMode ?: defaultSearchMode,
        listMode = listMode
      )
    )
  }

  fun forceRefresh() {
    forceUpdateOnNextSearchAction()
    searchAction.postValue(updateSearchAction(searchAction.value!!))
  }

  companion object {

    @VisibleForTesting
    fun generateStrictDomainRegex(domain: String): Regex? {
      // Valid domains do not contain path separators.
      if (domain.contains('/')) return null
      // Matches the start of a path component, which is either the start of the
      // string or a path separator.
      val prefix = """(?:^|/)"""
      val escapedFilter = Regex.escape(domain.replace("/", ""))
      // Matches either the filter literally or a strict subdomain of the filter term.
      // We allow a lot of freedom in what a subdomain is, as long as it is not an
      // email address.
      val subdomain = """(?:(?:[^/@]+\.)?$escapedFilter)"""
      // Matches the end of a path component, which is either the literal ".gpg" or a
      // path separator.
      val suffix = """(?:\.gpg|/)"""
      // Match any relative path with a component that is a subdomain of the filter.
      return Regex(prefix + subdomain + suffix)
    }
  }
}

private object PasswordItemDiffCallback : DiffUtil.ItemCallback<PasswordItem>() {

  override fun areItemsTheSame(oldItem: PasswordItem, newItem: PasswordItem) =
    oldItem.file.absolutePath == newItem.file.absolutePath

  override fun areContentsTheSame(oldItem: PasswordItem, newItem: PasswordItem) = oldItem == newItem
}

open class SearchableRepositoryAdapter<T : RecyclerView.ViewHolder>(
  private val layoutRes: Int,
  private val viewHolderCreator: (view: View) -> T,
  private val coroutineScope: CoroutineScope,
  private val viewHolderBinder: suspend T.(item: PasswordItem) -> Unit,
) : ListAdapter<PasswordItem, T>(PasswordItemDiffCallback), PopupTextProvider {

  fun <T : ItemDetailsLookup<String>> makeSelectable(
    recyclerView: RecyclerView,
    itemDetailsLookupCreator: (recyclerView: RecyclerView) -> T
  ) {
    selectionTracker =
      SelectionTracker.Builder(
          "SearchableRepositoryAdapter",
          recyclerView,
          itemKeyProvider,
          itemDetailsLookupCreator(recyclerView),
          StorageStrategy.createStringStorage()
        )
        .withSelectionPredicate(SelectionPredicates.createSelectAnything())
        .build()
        .apply {
          addObserver(
            object : SelectionTracker.SelectionObserver<String>() {
              override fun onSelectionChanged() {
                this@SearchableRepositoryAdapter.onSelectionChangedListener?.invoke(
                  requireSelectionTracker().selection
                )
              }
            }
          )
        }
  }

  private var onItemClickedListener: ((holder: T, item: PasswordItem) -> Unit)? = null
  open fun onItemClicked(
    listener: (holder: T, item: PasswordItem) -> Unit
  ): SearchableRepositoryAdapter<T> {
    check(onItemClickedListener == null) {
      "Only a single listener can be registered for onItemClicked"
    }
    onItemClickedListener = listener
    return this
  }

  private var onSelectionChangedListener: ((selection: Selection<String>) -> Unit)? = null
  open fun onSelectionChanged(
    listener: (selection: Selection<String>) -> Unit
  ): SearchableRepositoryAdapter<T> {
    check(onSelectionChangedListener == null) {
      "Only a single listener can be registered for onSelectionChanged"
    }
    onSelectionChangedListener = listener
    return this
  }

  private val itemKeyProvider =
    object : ItemKeyProvider<String>(SCOPE_MAPPED) {
      override fun getKey(position: Int) = getItem(position).stableId

      override fun getPosition(key: String) =
        (0 until itemCount).firstOrNull { getItem(it).stableId == key } ?: RecyclerView.NO_POSITION
    }

  private var selectionTracker: SelectionTracker<String>? = null
  fun requireSelectionTracker() = selectionTracker!!

  private val selectedFiles
    get() = requireSelectionTracker().selection.map { File(it) }

  fun getSelectedItems() = selectedFiles.map { it.toPasswordItem() }

  fun getPositionForFile(file: File) = itemKeyProvider.getPosition(file.absolutePath)

  final override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): T {
    val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
    return viewHolderCreator(view)
  }

  final override fun onBindViewHolder(holder: T, position: Int) {
    val item = getItem(position)
    holder.apply {
      coroutineScope.launch(Dispatchers.Main.immediate) { viewHolderBinder.invoke(holder, item) }
      selectionTracker?.let { itemView.isSelected = it.isSelected(item.stableId) }
      itemView.setOnClickListener {
        // Do not emit custom click events while the user is selecting items.
        if (selectionTracker?.hasSelection() != true) onItemClickedListener?.invoke(holder, item)
      }
    }
  }
  final override fun getPopupText(position: Int): String {
    return getItem(position).name[0].toString().uppercase(Locale.getDefault())
  }
}
