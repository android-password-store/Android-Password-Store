/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.passwords

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.content.edit
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.i
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.data.password.PasswordItem
import dev.msfjarvis.aps.data.repo.PasswordRepository
import dev.msfjarvis.aps.ui.crypto.BasePgpActivity.Companion.getLongName
import dev.msfjarvis.aps.ui.crypto.DecryptActivity
import dev.msfjarvis.aps.ui.crypto.PasswordCreationActivity
import dev.msfjarvis.aps.ui.dialogs.BasicBottomSheet
import dev.msfjarvis.aps.ui.dialogs.FolderCreationDialogFragment
import dev.msfjarvis.aps.ui.folderselect.SelectFolderActivity
import dev.msfjarvis.aps.ui.git.base.BaseGitActivity
import dev.msfjarvis.aps.ui.onboarding.activity.OnboardingActivity
import dev.msfjarvis.aps.ui.settings.DirectorySelectionActivity
import dev.msfjarvis.aps.ui.settings.SettingsActivity
import dev.msfjarvis.aps.util.autofill.AutofillMatcher
import dev.msfjarvis.aps.util.extensions.base64
import dev.msfjarvis.aps.util.extensions.commitChange
import dev.msfjarvis.aps.util.extensions.contains
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.isInsideRepository
import dev.msfjarvis.aps.util.extensions.isPermissionGranted
import dev.msfjarvis.aps.util.extensions.listFilesRecursively
import dev.msfjarvis.aps.util.extensions.requestInputFocusOnView
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.settings.AuthMode
import dev.msfjarvis.aps.util.settings.GitSettings
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import dev.msfjarvis.aps.util.shortcuts.ShortcutHandler
import dev.msfjarvis.aps.util.viewmodel.SearchableRepositoryViewModel
import java.io.File
import java.lang.Character.UnicodeBlock
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val PASSWORD_FRAGMENT_TAG = "PasswordsList"

@AndroidEntryPoint
class PasswordStore : BaseGitActivity() {

  @Inject lateinit var shortcutHandler: ShortcutHandler
  private lateinit var searchItem: MenuItem
  private val settings by lazy { sharedPrefs }

  private val model: SearchableRepositoryViewModel by viewModels {
    ViewModelProvider.AndroidViewModelFactory(application)
  }

  private val storagePermissionRequest =
    registerForActivityResult(RequestPermission()) { granted ->
      if (granted) checkLocalRepository()
    }

  private val directorySelectAction =
    registerForActivityResult(StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        checkLocalRepository()
      }
    }

  private val listRefreshAction =
    registerForActivityResult(StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        refreshPasswordList()
      }
    }

  private val passwordMoveAction =
    registerForActivityResult(StartActivityForResult()) { result ->
      val intentData = result.data ?: return@registerForActivityResult
      val filesToMove = requireNotNull(intentData.getStringArrayExtra("Files"))
      val target = File(requireNotNull(intentData.getStringExtra("SELECTED_FOLDER_PATH")))
      val repositoryPath = PasswordRepository.getRepositoryDirectory().absolutePath
      if (!target.isDirectory) {
        e { "Tried moving passwords to a non-existing folder." }
        return@registerForActivityResult
      }

      d { "Moving passwords to ${intentData.getStringExtra("SELECTED_FOLDER_PATH")}" }
      d { filesToMove.joinToString(", ") }

      lifecycleScope.launch(Dispatchers.IO) {
        for (file in filesToMove) {
          val source = File(file)
          if (!source.exists()) {
            e { "Tried moving something that appears non-existent." }
            continue
          }
          val destinationFile = File(target.absolutePath + "/" + source.name)
          val basename = source.nameWithoutExtension
          val sourceLongName = getLongName(requireNotNull(source.parent), repositoryPath, basename)
          val destinationLongName = getLongName(target.absolutePath, repositoryPath, basename)
          if (destinationFile.exists()) {
            e { "Trying to move a file that already exists." }
            withContext(Dispatchers.Main) {
              MaterialAlertDialogBuilder(this@PasswordStore)
                .setTitle(resources.getString(R.string.password_exists_title))
                .setMessage(
                  resources.getString(
                    R.string.password_exists_message,
                    destinationLongName,
                    sourceLongName
                  )
                )
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                  launch(Dispatchers.IO) { moveFile(source, destinationFile) }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
            }
          } else {
            launch(Dispatchers.IO) { moveFile(source, destinationFile) }
          }
        }
        when (filesToMove.size) {
          1 -> {
            val source = File(filesToMove[0])
            val basename = source.nameWithoutExtension
            val sourceLongName =
              getLongName(requireNotNull(source.parent), repositoryPath, basename)
            val destinationLongName = getLongName(target.absolutePath, repositoryPath, basename)
            withContext(Dispatchers.Main) {
              commitChange(
                resources.getString(
                  R.string.git_commit_move_text,
                  sourceLongName,
                  destinationLongName
                ),
              )
            }
          }
          else -> {
            val repoDir = PasswordRepository.getRepositoryDirectory().absolutePath
            val relativePath = getRelativePath("${target.absolutePath}/", repoDir)
            withContext(Dispatchers.Main) {
              commitChange(
                resources.getString(R.string.git_commit_move_multiple_text, relativePath),
              )
            }
          }
        }
      }
      refreshPasswordList()
      getPasswordFragment()?.dismissActionMode()
    }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    // open search view on search key, or Ctr+F
    if ((keyCode == KeyEvent.KEYCODE_SEARCH ||
        keyCode == KeyEvent.KEYCODE_F && event.isCtrlPressed) && !searchItem.isActionViewExpanded
    ) {
      searchItem.expandActionView()
      return true
    }

    // open search view on any printable character and query for it
    val c = event.unicodeChar.toChar()
    val printable = isPrintable(c)
    if (printable && !searchItem.isActionViewExpanded) {
      searchItem.expandActionView()
      (searchItem.actionView as SearchView).setQuery(c.toString(), true)
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  @SuppressLint("NewApi")
  override fun onCreate(savedInstanceState: Bundle?) {
    // If user opens app with permission granted then revokes and returns,
    // prevent attempt to create password list fragment
    var savedInstance = savedInstanceState
    if (savedInstanceState != null &&
        (!settings.getBoolean(PreferenceKeys.GIT_EXTERNAL, false) ||
          !isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    ) {
      savedInstance = null
    }
    super.onCreate(savedInstance)
    setContentView(R.layout.activity_pwdstore)

    model.currentDir.observe(this) { dir ->
      val basePath = PasswordRepository.getRepositoryDirectory().absoluteFile
      supportActionBar!!.apply {
        if (dir != basePath) title = dir.name else setTitle(R.string.app_name)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    refreshPasswordList()
  }

  override fun onResume() {
    super.onResume()
    if (settings.getBoolean(PreferenceKeys.GIT_EXTERNAL, false)) {
      hasRequiredStoragePermissions()
    } else {
      checkLocalRepository()
    }
    if (settings.getBoolean(PreferenceKeys.SEARCH_ON_START, false) && ::searchItem.isInitialized) {
      if (!searchItem.isActionViewExpanded) {
        searchItem.expandActionView()
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    val menuRes =
      when {
        gitSettings.authMode == AuthMode.None -> R.menu.main_menu_no_auth
        PasswordRepository.isGitRepo() -> R.menu.main_menu_git
        else -> R.menu.main_menu_non_git
      }
    menuInflater.inflate(menuRes, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    // Invalidation forces onCreateOptionsMenu to be called again. This is cheap and quick so
    // we can get by without any noticeable difference in performance.
    invalidateOptionsMenu()
    searchItem = menu.findItem(R.id.action_search)
    val searchView = searchItem.actionView as SearchView
    searchView.setOnQueryTextListener(
      object : OnQueryTextListener {
        override fun onQueryTextSubmit(s: String): Boolean {
          searchView.clearFocus()
          return true
        }

        override fun onQueryTextChange(s: String): Boolean {
          val filter = s.trim()
          // List the contents of the current directory if the user enters a blank
          // search term.
          if (filter.isEmpty())
            model.navigateTo(newDirectory = model.currentDir.value!!, pushPreviousLocation = false)
          else model.search(filter)
          return true
        }
      }
    )

    // When using the support library, the setOnActionExpandListener() method is
    // static and accepts the MenuItem object as an argument
    searchItem.setOnActionExpandListener(
      object : OnActionExpandListener {
        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
          refreshPasswordList()
          return true
        }

        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
          return true
        }
      }
    )
    if (settings.getBoolean(PreferenceKeys.SEARCH_ON_START, false)) {
      searchItem.expandActionView()
    }
    return super.onPrepareOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val id = item.itemId
    val initBefore =
      MaterialAlertDialogBuilder(this)
        .setMessage(resources.getString(R.string.creation_dialog_text))
        .setPositiveButton(resources.getString(R.string.dialog_ok), null)
    when (id) {
      R.id.user_pref -> {
        runCatching { startActivity(Intent(this, SettingsActivity::class.java)) }.onFailure { e ->
          e.printStackTrace()
        }
      }
      R.id.git_push -> {
        if (!PasswordRepository.isInitialized) {
          initBefore.show()
        } else {
          runGitOperation(GitOp.PUSH)
        }
      }
      R.id.git_pull -> {
        if (!PasswordRepository.isInitialized) {
          initBefore.show()
        } else {
          runGitOperation(GitOp.PULL)
        }
      }
      R.id.git_sync -> {
        if (!PasswordRepository.isInitialized) {
          initBefore.show()
        } else {
          runGitOperation(GitOp.SYNC)
        }
      }
      R.id.refresh -> refreshPasswordList()
      android.R.id.home -> onBackPressed()
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  override fun onBackPressed() {
    if (getPasswordFragment()?.onBackPressedInActivity() != true) super.onBackPressed()
  }

  private fun getPasswordFragment(): PasswordFragment? {
    return supportFragmentManager.findFragmentByTag(PASSWORD_FRAGMENT_TAG) as? PasswordFragment
  }

  fun clearSearch() {
    if (searchItem.isActionViewExpanded) searchItem.collapseActionView()
  }

  private fun runGitOperation(operation: GitOp) =
    lifecycleScope.launch {
      launchGitOperation(operation)
        .fold(
          success = { refreshPasswordList() },
          failure = { promptOnErrorHandler(it) },
        )
    }

  /**
   * Validates if storage permission is granted, and requests for it if not. The return value is
   * true if the permission has been granted.
   */
  private fun hasRequiredStoragePermissions(): Boolean {
    return if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      BasicBottomSheet.Builder(this)
        .setMessageRes(R.string.access_sdcard_text)
        .setPositiveButtonClickListener(getString(R.string.snackbar_action_grant)) {
          storagePermissionRequest.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        .build()
        .show(supportFragmentManager, "STORAGE_PERMISSION_MISSING")
      false
    } else {
      checkLocalRepository()
      true
    }
  }

  private fun checkLocalRepository() {
    val repo = PasswordRepository.initialize()
    if (repo == null) {
      directorySelectAction.launch(Intent(this, DirectorySelectionActivity::class.java))
    } else {
      checkLocalRepository(PasswordRepository.getRepositoryDirectory())
    }
  }

  private fun checkLocalRepository(localDir: File?) {
    if (localDir != null && settings.getBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, false)) {
      d { "Check, dir: ${localDir.absolutePath}" }
      // do not push the fragment if we already have it
      if (getPasswordFragment() == null || settings.getBoolean(PreferenceKeys.REPO_CHANGED, false)
      ) {
        settings.edit { putBoolean(PreferenceKeys.REPO_CHANGED, false) }
        val args = Bundle()
        args.putString(REQUEST_ARG_PATH, PasswordRepository.getRepositoryDirectory().absolutePath)

        // if the activity was started from the autofill settings, the
        // intent is to match a clicked pwd with app. pass this to fragment
        if (intent.getBooleanExtra("matchWith", false)) {
          args.putBoolean("matchWith", true)
        }
        supportActionBar?.apply {
          show()
          setDisplayHomeAsUpEnabled(false)
        }
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.commit {
          replace(R.id.main_layout, PasswordFragment.newInstance(args), PASSWORD_FRAGMENT_TAG)
        }
      }
    } else {
      startActivity(Intent(this, OnboardingActivity::class.java))
    }
  }

  private fun getRelativePath(fullPath: String, repositoryPath: String): String {
    return fullPath.replace(repositoryPath, "").replace("/+".toRegex(), "/")
  }

  fun decryptPassword(item: PasswordItem) {
    val authDecryptIntent = item.createAuthEnabledIntent(this)
    val decryptIntent =
      (authDecryptIntent.clone() as Intent).setComponent(
        ComponentName(this, DecryptActivity::class.java)
      )

    startActivity(decryptIntent)

    // Adds shortcut
    shortcutHandler.addDynamicShortcut(item, authDecryptIntent)
  }

  private fun validateState(): Boolean {
    if (!PasswordRepository.isInitialized) {
      MaterialAlertDialogBuilder(this)
        .setMessage(resources.getString(R.string.creation_dialog_text))
        .setPositiveButton(resources.getString(R.string.dialog_ok), null)
        .show()
      return false
    }
    return true
  }

  fun createPassword() {
    if (!validateState()) return
    val currentDir = currentDir
    i { "Adding file to : ${currentDir.absolutePath}" }
    val intent = Intent(this, PasswordCreationActivity::class.java)
    intent.putExtra("FILE_PATH", currentDir.absolutePath)
    intent.putExtra("REPO_PATH", PasswordRepository.getRepositoryDirectory().absolutePath)
    listRefreshAction.launch(intent)
  }

  fun createFolder() {
    if (!validateState()) return
    FolderCreationDialogFragment.newInstance(currentDir.path).show(supportFragmentManager, null)
  }

  fun deletePasswords(selectedItems: List<PasswordItem>) {
    var size = 0
    selectedItems.forEach {
      if (it.file.isFile) size++ else size += it.file.listFilesRecursively().size
    }
    if (size == 0) {
      selectedItems.map { item -> item.file.deleteRecursively() }
      refreshPasswordList()
      return
    }
    MaterialAlertDialogBuilder(this)
      .setMessage(resources.getQuantityString(R.plurals.delete_dialog_text, size, size))
      .setPositiveButton(resources.getString(R.string.dialog_yes)) { _, _ ->
        val filesToDelete = arrayListOf<File>()
        selectedItems.forEach { item ->
          if (item.file.isDirectory) filesToDelete.addAll(item.file.listFilesRecursively())
          else filesToDelete.add(item.file)
        }
        selectedItems.map { item -> item.file.deleteRecursively() }
        refreshPasswordList()
        AutofillMatcher.updateMatches(applicationContext, delete = filesToDelete)
        val fmt =
          selectedItems.joinToString(separator = ", ") { item ->
            item.file.toRelativeString(PasswordRepository.getRepositoryDirectory())
          }
        lifecycleScope.launch {
          commitChange(
            resources.getString(R.string.git_commit_remove_text, fmt),
          )
        }
      }
      .setNegativeButton(resources.getString(R.string.dialog_no), null)
      .show()
  }

  fun movePasswords(values: List<PasswordItem>) {
    val intent = Intent(this, SelectFolderActivity::class.java)
    val fileLocations = values.map { it.file.absolutePath }.toTypedArray()
    intent.putExtra("Files", fileLocations)
    passwordMoveAction.launch(intent)
  }

  enum class CategoryRenameError(val resource: Int) {
    None(0),
    EmptyField(R.string.message_category_error_empty_field),
    CategoryExists(R.string.message_category_error_category_exists),
    DestinationOutsideRepo(R.string.message_error_destination_outside_repo),
  }

  /**
   * Prompt the user with a new category name to assign, if the new category forms/leads a path
   * (i.e. contains "/"), intermediate directories will be created and new category will be placed
   * inside.
   *
   * @param oldCategory The category to change its name
   * @param error Determines whether to show an error to the user in the alert dialog, this error
   * may be due to the new category the user entered already exists or the field was empty or the
   * destination path is outside the repository
   *
   * @see [CategoryRenameError]
   * @see [isInsideRepository]
   */
  private fun renameCategory(
    oldCategory: PasswordItem,
    error: CategoryRenameError = CategoryRenameError.None
  ) {
    val view = layoutInflater.inflate(R.layout.folder_dialog_fragment, null)
    val newCategoryEditText = view.findViewById<TextInputEditText>(R.id.folder_name_text)

    if (error != CategoryRenameError.None) {
      newCategoryEditText.error = getString(error.resource)
    }

    val dialog =
      MaterialAlertDialogBuilder(this)
        .setTitle(R.string.title_rename_folder)
        .setView(view)
        .setMessage(getString(R.string.message_rename_folder, oldCategory.name))
        .setPositiveButton(R.string.dialog_ok) { _, _ ->
          val newCategory = File("${oldCategory.file.parent}/${newCategoryEditText.text}")
          when {
            newCategoryEditText.text.isNullOrBlank() ->
              renameCategory(oldCategory, CategoryRenameError.EmptyField)
            newCategory.exists() -> renameCategory(oldCategory, CategoryRenameError.CategoryExists)
            !newCategory.isInsideRepository() ->
              renameCategory(oldCategory, CategoryRenameError.DestinationOutsideRepo)
            else ->
              lifecycleScope.launch(Dispatchers.IO) {
                moveFile(oldCategory.file, newCategory)

                // associate the new category with the last category's timestamp in
                // history
                val preference =
                  getSharedPreferences("recent_password_history", Context.MODE_PRIVATE)
                val timestamp = preference.getString(oldCategory.file.absolutePath.base64())
                if (timestamp != null) {
                  preference.edit {
                    remove(oldCategory.file.absolutePath.base64())
                    putString(newCategory.absolutePath.base64(), timestamp)
                  }
                }

                withContext(Dispatchers.Main) {
                  commitChange(
                    resources.getString(
                      R.string.git_commit_move_text,
                      oldCategory.name,
                      newCategory.name
                    ),
                  )
                }
              }
          }
        }
        .setNegativeButton(R.string.dialog_skip, null)
        .create()

    dialog.requestInputFocusOnView<TextInputEditText>(R.id.folder_name_text)
    dialog.show()
  }

  fun renameCategory(categories: List<PasswordItem>) {
    for (oldCategory in categories) {
      renameCategory(oldCategory)
    }
  }

  /**
   * Refreshes the password list by re-executing the last navigation or search action, preserving
   * the navigation stack and scroll position. If the current directory no longer exists, navigation
   * is reset to the repository root. If the optional [target] argument is provided, it will be
   * entered if it is a directory or scrolled into view if it is a file (both inside the current
   * directory).
   */
  fun refreshPasswordList(target: File? = null) {
    val plist = getPasswordFragment()
    if (target?.isDirectory == true && model.currentDir.value?.contains(target) == true) {
      plist?.navigateTo(target)
    } else if (target?.isFile == true && model.currentDir.value?.contains(target) == true) {
      // Creating new passwords is handled by an activity, so we will refresh in onStart.
      plist?.scrollToOnNextRefresh(target)
    } else if (model.currentDir.value?.isDirectory == true) {
      model.forceRefresh()
    } else {
      model.reset()
      supportActionBar!!.setDisplayHomeAsUpEnabled(false)
    }
  }

  private val currentDir: File
    get() = getPasswordFragment()?.currentDir ?: PasswordRepository.getRepositoryDirectory()

  private suspend fun moveFile(source: File, destinationFile: File) {
    val sourceDestinationMap =
      if (source.isDirectory) {
        destinationFile.mkdirs()
        // Recursively list all files (not directories) below `source`, then
        // obtain the corresponding target file by resolving the relative path
        // starting at the destination folder.
        source.listFilesRecursively().associateWith {
          destinationFile.resolve(it.relativeTo(source))
        }
      } else {
        mapOf(source to destinationFile)
      }
    if (!source.renameTo(destinationFile)) {
      e { "Something went wrong while moving $source to $destinationFile." }
      withContext(Dispatchers.Main) {
        MaterialAlertDialogBuilder(this@PasswordStore)
          .setTitle(R.string.password_move_error_title)
          .setMessage(getString(R.string.password_move_error_message, source, destinationFile))
          .setCancelable(true)
          .setPositiveButton(android.R.string.ok, null)
          .show()
      }
    } else {
      AutofillMatcher.updateMatches(this, sourceDestinationMap)
    }
  }

  fun matchPasswordWithApp(item: PasswordItem) {
    val path =
      item
        .file
        .absolutePath
        .replace(PasswordRepository.getRepositoryDirectory().toString() + "/", "")
        .replace(".gpg", "")
    val data = Intent()
    data.putExtra("path", path)
    setResult(RESULT_OK, data)
    finish()
  }

  companion object {

    const val REQUEST_ARG_PATH = "PATH"
    private fun isPrintable(c: Char): Boolean {
      val block = UnicodeBlock.of(c)
      return (!Character.isISOControl(c) && block != null && block !== UnicodeBlock.SPECIALS)
    }
  }
}
