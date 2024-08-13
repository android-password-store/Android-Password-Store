/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.passwords

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.content.edit
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.data.password.PasswordItem
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.ui.crypto.BasePGPActivity
import app.passwordstore.ui.crypto.BasePGPActivity.Companion.getLongName
import app.passwordstore.ui.crypto.DecryptActivity
import app.passwordstore.ui.crypto.PasswordCreationActivity
import app.passwordstore.ui.dialogs.FolderCreationDialogFragment
import app.passwordstore.ui.folderselect.SelectFolderActivity
import app.passwordstore.ui.git.base.BaseGitActivity
import app.passwordstore.ui.onboarding.activity.OnboardingActivity
import app.passwordstore.ui.settings.SettingsActivity
import app.passwordstore.util.autofill.AutofillMatcher
import app.passwordstore.util.extensions.base64
import app.passwordstore.util.extensions.commitChange
import app.passwordstore.util.extensions.contains
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.extensions.isInsideRepository
import app.passwordstore.util.extensions.launchActivity
import app.passwordstore.util.extensions.listFilesRecursively
import app.passwordstore.util.extensions.sharedPrefs
import app.passwordstore.util.settings.AuthMode
import app.passwordstore.util.settings.PreferenceKeys
import app.passwordstore.util.shortcuts.ShortcutHandler
import app.passwordstore.util.viewmodel.SearchableRepositoryViewModel
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.lang.Character.UnicodeBlock
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.logcat

const val PASSWORD_FRAGMENT_TAG = "PasswordsList"

@AndroidEntryPoint
class PasswordStore : BaseGitActivity() {

  @Inject lateinit var shortcutHandler: ShortcutHandler
  private lateinit var searchItem: MenuItem
  private val settings by lazy { sharedPrefs }

  private val model: SearchableRepositoryViewModel by viewModels()

  private val listRefreshAction =
    registerForActivityResult(StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        refreshPasswordList()
      }
    }

  private val passwordMoveAction =
    registerForActivityResult(StartActivityForResult()) { result ->
      val intentData = result.data ?: return@registerForActivityResult
      val filesToMove =
        requireNotNull(intentData.getStringArrayExtra("Files")) {
          "'Files' intent extra must be set"
        }
      val target =
        File(
          requireNotNull(intentData.getStringExtra("SELECTED_FOLDER_PATH")) {
            "'SELECTED_FOLDER_PATH' intent extra must be set"
          }
        )
      val repositoryPath = PasswordRepository.getRepositoryDirectory().absolutePath
      if (!target.isDirectory) {
        logcat(ERROR) { "Tried moving passwords to a non-existing folder." }
        return@registerForActivityResult
      }

      logcat { "Moving passwords to ${intentData.getStringExtra("SELECTED_FOLDER_PATH")}" }
      logcat { filesToMove.joinToString(", ") }

      lifecycleScope.launch(dispatcherProvider.io()) {
        for (file in filesToMove) {
          val source = File(file)
          if (!source.exists()) {
            logcat(ERROR) { "Tried moving something that appears non-existent." }
            continue
          }
          val destinationFile = File(target.absolutePath + "/" + source.name)
          val basename = source.nameWithoutExtension
          val sourceLongName =
            getLongName(
              requireNotNull(source.parent) { "$file has no parent" },
              repositoryPath,
              basename,
            )
          val destinationLongName = getLongName(target.absolutePath, repositoryPath, basename)
          if (destinationFile.exists()) {
            logcat(ERROR) { "Trying to move a file that already exists." }
            withContext(dispatcherProvider.main()) {
              MaterialAlertDialogBuilder(this@PasswordStore)
                .setTitle(resources.getString(R.string.password_exists_title))
                .setMessage(
                  resources.getString(
                    R.string.password_exists_message,
                    destinationLongName,
                    sourceLongName,
                  )
                )
                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                  launch(dispatcherProvider.io()) { moveFile(source, destinationFile) }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
            }
          } else {
            launch(dispatcherProvider.io()) { moveFile(source, destinationFile) }
          }
        }
        when (filesToMove.size) {
          1 -> {
            val source = File(filesToMove[0])
            val basename = source.nameWithoutExtension
            val sourceLongName =
              getLongName(
                requireNotNull(source.parent) { "$basename has no parent" },
                repositoryPath,
                basename,
              )
            val destinationLongName = getLongName(target.absolutePath, repositoryPath, basename)
            withContext(dispatcherProvider.main()) {
              commitChange(
                resources.getString(
                  R.string.git_commit_move_text,
                  sourceLongName,
                  destinationLongName,
                )
              )
            }
          }
          else -> {
            val repoDir = PasswordRepository.getRepositoryDirectory().absolutePath
            val relativePath = getRelativePath("${target.absolutePath}/", repoDir)
            withContext(dispatcherProvider.main()) {
              commitChange(
                resources.getString(R.string.git_commit_move_multiple_text, relativePath)
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
    if (
      (keyCode == KeyEvent.KEYCODE_SEARCH ||
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_pwdstore)

    lifecycleScope.launch {
      model.currentDir.flowWithLifecycle(lifecycle).collect { dir ->
        val basePath = PasswordRepository.getRepositoryDirectory().absoluteFile
        supportActionBar?.apply {
          if (dir != basePath) title = dir.name else setTitle(R.string.app_name)
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    refreshPasswordList()
  }

  override fun onResume() {
    super.onResume()
    checkLocalRepository()
    if (settings.getBoolean(PreferenceKeys.SEARCH_ON_START, false) && ::searchItem.isInitialized) {
      if (!searchItem.isActionViewExpanded) {
        searchItem.expandActionView()
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
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
            model.navigateTo(newDirectory = model.currentDir.value, pushPreviousLocation = false)
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
        runCatching { launchActivity(SettingsActivity::class.java) }
          .onFailure { e -> e.printStackTrace() }
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
      android.R.id.home -> {
        @Suppress("DEPRECATION") onBackPressed()
      }
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  @Deprecated("Deprecated in Java")
  @Suppress("DEPRECATION")
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
        .fold(success = { refreshPasswordList() }, failure = { promptOnErrorHandler(it) })
    }

  private fun checkLocalRepository() {
    PasswordRepository.initialize()
    checkLocalRepository(PasswordRepository.getRepositoryDirectory())
  }

  private fun checkLocalRepository(localDir: File?) {
    if (localDir != null && settings.getBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, false)) {
      // do not push the fragment if we already have it
      if (
        getPasswordFragment() == null || settings.getBoolean(PreferenceKeys.REPO_CHANGED, false)
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
      launchActivity(OnboardingActivity::class.java)
    }
  }

  private fun getRelativePath(fullPath: String, repositoryPath: String): String {
    return fullPath.replace(repositoryPath, "").replace("/+".toRegex(), "/")
  }

  fun decryptPassword(item: PasswordItem) {
    val authDecryptIntent = item.createAuthEnabledIntent(this)
    val decryptIntent =
      Intent(authDecryptIntent).setComponent(ComponentName(this, DecryptActivity::class.java))

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
    logcat(INFO) { "Adding file to : ${currentDir.absolutePath}" }
    val intent = Intent(this, PasswordCreationActivity::class.java)
    intent.putExtra(BasePGPActivity.EXTRA_FILE_PATH, currentDir.absolutePath)
    intent.putExtra(
      BasePGPActivity.EXTRA_REPO_PATH,
      PasswordRepository.getRepositoryDirectory().absolutePath,
    )
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
          commitChange(resources.getString(R.string.git_commit_remove_text, fmt))
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
   *   may be due to the new category the user entered already exists or the field was empty or the
   *   destination path is outside the repository
   * @see [CategoryRenameError]
   * @see [isInsideRepository]
   */
  private fun renameCategory(
    oldCategory: PasswordItem,
    error: CategoryRenameError = CategoryRenameError.None,
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
              lifecycleScope.launch(dispatcherProvider.io()) {
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

                withContext(dispatcherProvider.main()) {
                  commitChange(
                    resources.getString(
                      R.string.git_commit_move_text,
                      oldCategory.name,
                      newCategory.name,
                    )
                  )
                }
              }
          }
        }
        .setNegativeButton(R.string.dialog_skip, null)
        .create()

    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
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
    if (target?.isDirectory == true && model.currentDir.value.contains(target)) {
      plist?.navigateTo(target)
    } else if (target?.isFile == true && model.currentDir.value.contains(target)) {
      // Creating new passwords is handled by an activity, so we will refresh in onStart.
      plist?.scrollToOnNextRefresh(target)
    } else if (model.currentDir.value.isDirectory) {
      model.forceRefresh()
    } else {
      model.reset()
      supportActionBar?.setDisplayHomeAsUpEnabled(false)
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
      logcat(ERROR) { "Something went wrong while moving $source to $destinationFile." }
      withContext(dispatcherProvider.main()) {
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
      item.file.absolutePath
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
