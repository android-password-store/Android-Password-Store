/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo.Builder
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.preference.PreferenceManager
import com.github.ajalt.timberkt.Timber.tag
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.i
import com.github.ajalt.timberkt.w
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.autofill.oreo.AutofillMatcher
import com.zeapo.pwdstore.autofill.oreo.BrowserAutofillSupportLevel
import com.zeapo.pwdstore.autofill.oreo.getInstalledBrowsersWithAutofillSupportLevel
import com.zeapo.pwdstore.crypto.PasswordCreationActivity
import com.zeapo.pwdstore.crypto.DecryptActivity
import com.zeapo.pwdstore.crypto.PgpActivity
import com.zeapo.pwdstore.crypto.PgpActivity.Companion.getLongName
import com.zeapo.pwdstore.git.BaseGitActivity
import com.zeapo.pwdstore.git.GitAsyncTask
import com.zeapo.pwdstore.git.GitOperation
import com.zeapo.pwdstore.git.GitOperationActivity
import com.zeapo.pwdstore.git.GitServerConfigActivity
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.ui.dialogs.FolderCreationDialogFragment
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.closeRepository
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.createRepository
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.getPasswords
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.getRepository
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.getRepositoryDirectory
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.initialize
import com.zeapo.pwdstore.utils.PasswordRepository.Companion.isInitialized
import com.zeapo.pwdstore.utils.PasswordRepository.PasswordSortOrder.Companion.getSortOrder
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.revwalk.RevCommit
import java.io.File
import java.lang.Character.UnicodeBlock
import java.util.Stack

class PasswordStore : AppCompatActivity() {

    private lateinit var activity: PasswordStore
    private lateinit var searchItem: MenuItem
    private lateinit var searchView: SearchView
    private lateinit var settings: SharedPreferences
    private var plist: PasswordFragment? = null
    private var shortcutManager: ShortcutManager? = null

    private val model: SearchableRepositoryViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory(application)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // open search view on search key, or Ctr+F
        if ((keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_F && event.isCtrlPressed) &&
            !searchItem.isActionViewExpanded) {
            searchItem.expandActionView()
            return true
        }

        // open search view on any printable character and query for it
        val c = event.unicodeChar.toChar()
        val printable = isPrintable(c)
        if (printable && !searchItem.isActionViewExpanded) {
            searchItem.expandActionView()
            searchView.setQuery(c.toString(), true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        activity = this
        settings = PreferenceManager.getDefaultSharedPreferences(this.applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            shortcutManager = getSystemService()
        }

        // If user opens app with permission granted then revokes and returns,
        // prevent attempt to create password list fragment
        var savedInstance = savedInstanceState
        if (savedInstanceState != null && (!settings.getBoolean("git_external", false) ||
                ContextCompat.checkSelfPermission(
                    activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            savedInstance = null
        }
        super.onCreate(savedInstance)
        setContentView(R.layout.activity_pwdstore)

        // If user is eligible for Oreo autofill, prompt them to switch.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !settings.getBoolean(PREFERENCE_SEEN_AUTOFILL_ONBOARDING, false)) {
            MaterialAlertDialogBuilder(this).run {
                @SuppressLint("InflateParams")
                val layout =
                    layoutInflater.inflate(R.layout.oreo_autofill_instructions, null)
                layout.findViewById<AppCompatTextView>(R.id.intro_text).setText(R.string.autofill_onboarding_dialog_message)
                val supportedBrowsersTextView =
                    layout.findViewById<AppCompatTextView>(R.id.supportedBrowsers)
                supportedBrowsersTextView.text =
                    getInstalledBrowsersWithAutofillSupportLevel(context).joinToString(
                        separator = "\n"
                    ) {
                        val appLabel = it.first
                        val supportDescription = when (it.second) {
                            BrowserAutofillSupportLevel.None -> getString(R.string.oreo_autofill_no_support)
                            BrowserAutofillSupportLevel.FlakyFill -> getString(R.string.oreo_autofill_flaky_fill_support)
                            BrowserAutofillSupportLevel.PasswordFill -> getString(R.string.oreo_autofill_password_fill_support)
                            BrowserAutofillSupportLevel.GeneralFill -> getString(R.string.oreo_autofill_general_fill_support)
                            BrowserAutofillSupportLevel.GeneralFillAndSave -> getString(R.string.oreo_autofill_general_fill_and_save_support)
                        }
                        "$appLabel: $supportDescription"
                    }
                setView(layout)
                setTitle(R.string.autofill_onboarding_dialog_title)
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                    })
                }
                setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                setOnDismissListener {
                    settings.edit { putBoolean(PREFERENCE_SEEN_AUTOFILL_ONBOARDING, true) }
                }
                show()
            }
        }

        model.currentDir.observe(this) { dir ->
            val basePath = getRepositoryDirectory(applicationContext).absoluteFile
            supportActionBar!!.apply {
                if (dir != basePath)
                    title = dir.name
                else
                    setTitle(R.string.app_name)
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        // do not attempt to checkLocalRepository() if no storage permission: immediate crash
        if (settings.getBoolean("git_external", false)) {
            hasRequiredStoragePermissions(true)
        } else {
            checkLocalRepository()
        }
        if (settings.getBoolean("search_on_start", false) && ::searchItem.isInitialized) {
            if (!searchItem.isActionViewExpanded) {
                searchItem.expandActionView()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // If request is cancelled, the result arrays are empty.
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkLocalRepository()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val menuRes = when {
            ConnectionMode.fromString(settings.getString("git_remote_auth", null))
                == ConnectionMode.None -> R.menu.main_menu_no_auth
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
        searchView = searchItem.actionView as SearchView
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
                        model.navigateTo(
                            newDirectory = model.currentDir.value!!,
                            pushPreviousLocation = false
                        )
                    else
                        model.search(filter)
                    return true
                }
            })

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
            })
        if (settings.getBoolean("search_on_start", false)) {
            searchItem.expandActionView()
        }
        return super.onPrepareOptionsMenu(menu)
    }

    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        val intent: Intent
        val initBefore = MaterialAlertDialogBuilder(this)
            .setMessage(resources.getString(R.string.creation_dialog_text))
            .setPositiveButton(resources.getString(R.string.dialog_ok), null)
        when (id) {
            R.id.user_pref -> {
                try {
                    intent = Intent(this, UserPreference::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return true
            }
            R.id.git_push -> {
                if (!isInitialized) {
                    initBefore.show()
                    return false
                }
                intent = Intent(this, GitOperationActivity::class.java)
                intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, BaseGitActivity.REQUEST_PUSH)
                startActivityForResult(intent, BaseGitActivity.REQUEST_PUSH)
                return true
            }
            R.id.git_pull -> {
                if (!isInitialized) {
                    initBefore.show()
                    return false
                }
                intent = Intent(this, GitOperationActivity::class.java)
                intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, BaseGitActivity.REQUEST_PULL)
                startActivityForResult(intent, BaseGitActivity.REQUEST_PULL)
                return true
            }
            R.id.git_sync -> {
                if (!isInitialized) {
                    initBefore.show()
                    return false
                }
                intent = Intent(this, GitOperationActivity::class.java)
                intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, BaseGitActivity.REQUEST_SYNC)
                startActivityForResult(intent, BaseGitActivity.REQUEST_SYNC)
                return true
            }
            R.id.refresh -> {
                refreshPasswordList()
                return true
            }
            android.R.id.home -> onBackPressed()
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        plist = null
        super.onDestroy()
    }

    fun clearSearch() {
        if (searchItem.isActionViewExpanded)
            searchItem.collapseActionView()
    }

    private fun createRepository() {
        if (!isInitialized) {
            initialize(this)
        }
        val localDir = getRepositoryDirectory(applicationContext)
        try {
            check(localDir.mkdir()) { "Failed to create directory!" }
            createRepository(localDir)
            if (File(localDir.absolutePath + "/.gpg-id").createNewFile()) {
                settings.edit { putBoolean("repository_initialized", true) }
            } else {
                throw IllegalStateException("Failed to initialize repository state.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (!localDir.delete()) {
                tag(TAG).d { "Failed to delete local repository" }
            }
            return
        }
        checkLocalRepository()
    }

    private fun initializeRepositoryInfo() {
        val externalRepo = settings.getBoolean("git_external", false)
        val externalRepoPath = settings.getString("git_external_repo", null)
        if (externalRepo && !hasRequiredStoragePermissions()) {
            return
        }
        if (externalRepo && externalRepoPath != null) {
            val dir = File(externalRepoPath)
            if (dir.exists() && dir.isDirectory &&
                getPasswords(dir, getRepositoryDirectory(this), sortOrder).isNotEmpty()) {
                closeRepository()
                checkLocalRepository()
                return // if not empty, just show me the passwords!
            }
        }
        val keyIds = settings.getStringSet("openpgp_key_ids_set", HashSet())
        if (keyIds != null && keyIds.isEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setMessage(resources.getString(R.string.key_dialog_text))
                .setPositiveButton(resources.getString(R.string.dialog_positive)) { _, _ ->
                    val intent = Intent(activity, UserPreference::class.java)
                    startActivityForResult(intent, BaseGitActivity.REQUEST_INIT)
                }
                .setNegativeButton(resources.getString(R.string.dialog_negative), null)
                .show()
        }
        createRepository()
    }

    /**
     * Validates if storage permission is granted, and requests for it if not. The return value
     * is true if the permission has been granted.
     */
    private fun hasRequiredStoragePermissions(checkLocalRepo: Boolean = false): Boolean {
        return if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(
                findViewById(R.id.main_layout),
                getString(R.string.access_sdcard_text),
                Snackbar.LENGTH_INDEFINITE
            ).run {
                setAction(getString(R.string.snackbar_action_grant)) {
                    ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        REQUEST_EXTERNAL_STORAGE
                    )
                    dismiss()
                }
                show()
            }
            false
        } else {
            if (checkLocalRepo)
                checkLocalRepository()
            true
        }
    }

    private fun checkLocalRepository() {
        val repo = initialize(this)
        if (repo == null) {
            val intent = Intent(activity, UserPreference::class.java)
            intent.putExtra("operation", "git_external")
            startActivityForResult(intent, HOME)
        } else {
            checkLocalRepository(getRepositoryDirectory(applicationContext))
        }
    }

    private fun checkLocalRepository(localDir: File?) {
        if (localDir != null && settings.getBoolean("repository_initialized", false)) {
            tag(TAG).d { "Check, dir: ${localDir.absolutePath}" }
            // do not push the fragment if we already have it
            if (supportFragmentManager.findFragmentByTag("PasswordsList") == null ||
                settings.getBoolean("repo_changed", false)) {
                settings.edit { putBoolean("repo_changed", false) }
                plist = PasswordFragment()
                val args = Bundle()
                args.putString(REQUEST_ARG_PATH, getRepositoryDirectory(applicationContext).absolutePath)

                // if the activity was started from the autofill settings, the
                // intent is to match a clicked pwd with app. pass this to fragment
                if (intent.getBooleanExtra("matchWith", false)) {
                    args.putBoolean("matchWith", true)
                }
                plist!!.arguments = args
                supportActionBar!!.show()
                supportActionBar!!.setDisplayHomeAsUpEnabled(false)
                supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                supportFragmentManager.commit {
                    replace(R.id.main_layout, plist!!, "PasswordsList")
                }
            }
        } else {
            supportActionBar!!.hide()
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            supportFragmentManager.commit {
                replace(R.id.main_layout, ToCloneOrNot())
            }
        }
    }

    override fun onBackPressed() {
        if (plist?.onBackPressedInActivity() != true)
            super.onBackPressed()
    }

    private fun getRelativePath(fullPath: String, repositoryPath: String): String {
        return fullPath.replace(repositoryPath, "").replace("/+".toRegex(), "/")
    }

    private fun getLastChangedTimestamp(fullPath: String): Long {
        val repoPath = getRepositoryDirectory(this)
        val repository = getRepository(repoPath)
        if (repository == null) {
            tag(TAG).d { "getLastChangedTimestamp: No git repository" }
            return File(fullPath).lastModified()
        }
        val git = Git(repository)
        val relativePath = getRelativePath(fullPath, repoPath.absolutePath).substring(1) // Removes leading '/'
        val iterator: Iterator<RevCommit>
        iterator = try {
            git.log().addPath(relativePath).call().iterator()
        } catch (e: GitAPIException) {
            tag(TAG).e(e) { "getLastChangedTimestamp: GITAPIException" }
            return -1
        }
        if (!iterator.hasNext()) {
            tag(TAG).w { "getLastChangedTimestamp: No commits for file: $relativePath" }
            return -1
        }
        return iterator.next().commitTime.toLong() * 1000
    }

    fun decryptPassword(item: PasswordItem) {
        val decryptIntent = Intent(this, DecryptActivity::class.java)
        val authDecryptIntent = Intent(this, LaunchActivity::class.java)
        for (intent in arrayOf(decryptIntent, authDecryptIntent)) {
            intent.putExtra("NAME", item.toString())
            intent.putExtra("FILE_PATH", item.file.absolutePath)
            intent.putExtra("REPO_PATH", getRepositoryDirectory(applicationContext).absolutePath)
            intent.putExtra("LAST_CHANGED_TIMESTAMP", getLastChangedTimestamp(item.file.absolutePath))
            intent.putExtra("OPERATION", "DECRYPT")
        }

        // Adds shortcut
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcut = Builder(this, item.fullPathToParent)
                .setShortLabel(item.toString())
                .setLongLabel(item.fullPathToParent + item.toString())
                .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(authDecryptIntent.setAction("DECRYPT_PASS")) // Needs action
                .build()
            val shortcuts = shortcutManager!!.dynamicShortcuts
            if (shortcuts.size >= shortcutManager!!.maxShortcutCountPerActivity && shortcuts.size > 0) {
                shortcuts.removeAt(shortcuts.size - 1)
                shortcuts.add(0, shortcut)
                shortcutManager!!.dynamicShortcuts = shortcuts
            } else {
                shortcutManager!!.addDynamicShortcuts(listOf(shortcut))
            }
        }
        startActivityForResult(decryptIntent, REQUEST_CODE_DECRYPT_AND_VERIFY)
    }

    fun editPassword(item: PasswordItem) {
        val intent = Intent(this, PgpActivity::class.java)
        intent.putExtra("NAME", item.toString())
        intent.putExtra("FILE_PATH", item.file.absolutePath)
        intent.putExtra("PARENT_PATH", item.file.parentFile!!.absolutePath)
        intent.putExtra("REPO_PATH", getRepositoryDirectory(applicationContext).absolutePath)
        intent.putExtra("OPERATION", "EDIT")
        startActivityForResult(intent, REQUEST_CODE_EDIT)
    }

    private fun validateState(): Boolean {
        if (!isInitialized) {
            MaterialAlertDialogBuilder(this)
                .setMessage(resources.getString(R.string.creation_dialog_text))
                .setPositiveButton(resources.getString(R.string.dialog_ok), null)
                .show()
            return false
        }
        if (settings.getStringSet("openpgp_key_ids_set", HashSet()).isNullOrEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.no_key_selected_dialog_title))
                .setMessage(resources.getString(R.string.no_key_selected_dialog_text))
                .setPositiveButton(resources.getString(R.string.dialog_ok)) { _, _ ->
                    val intent = Intent(activity, UserPreference::class.java)
                    startActivity(intent)
                }
                .show()
            return false
        }
        return true
    }

    fun createPassword() {
        if (!validateState()) return
        val currentDir = currentDir
        tag(TAG).i { "Adding file to : ${currentDir.absolutePath}" }
        val intent = Intent(this, PasswordCreationActivity::class.java)
        intent.putExtra("FILE_PATH", currentDir.absolutePath)
        intent.putExtra("REPO_PATH", getRepositoryDirectory(applicationContext).absolutePath)
        startActivityForResult(intent, REQUEST_CODE_ENCRYPT)
    }

    fun createFolder() {
        if (!validateState()) return
        FolderCreationDialogFragment.newInstance(currentDir.path).show(supportFragmentManager, null)
    }

    // deletes passwords in order from top to bottom
    fun deletePasswords(selectedItems: Stack<PasswordItem>) {
        if (selectedItems.isEmpty()) {
            refreshPasswordList()
            return
        }
        val item = selectedItems.pop()
        MaterialAlertDialogBuilder(this)
            .setMessage(resources.getString(R.string.delete_dialog_text, item.longName))
            .setPositiveButton(resources.getString(R.string.dialog_yes)) { _, _ ->
                val filesToDelete = if (item.file.isDirectory) {
                    FileUtils.listFiles(item.file, null, true)
                } else {
                    listOf(item.file)
                }
                AutofillMatcher.updateMatches(applicationContext, delete = filesToDelete)
                item.file.deleteRecursively()
                commitChange(resources.getString(R.string.git_commit_remove_text, item.longName))
                deletePasswords(selectedItems)
            }
            .setNegativeButton(resources.getString(R.string.dialog_no)) { _, _ ->
                deletePasswords(selectedItems)
            }
            .show()
    }

    fun movePasswords(values: List<PasswordItem>) {
        val intent = Intent(this, SelectFolderActivity::class.java)
        val fileLocations = ArrayList<String>()
        for ((_, _, _, file) in values) {
            fileLocations.add(file.absolutePath)
        }
        intent.putExtra("Files", fileLocations)
        intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, "SELECTFOLDER")
        startActivityForResult(intent, REQUEST_CODE_SELECT_FOLDER)
    }

    /**
     * Resets navigation to the repository root and refreshes the password list accordingly.
     *
     * Use this rather than [refreshPasswordList] after major file system operations that may remove
     * the current directory and thus require a full reset of the navigation stack.
     */
    fun resetPasswordList() {
        model.reset()
        supportActionBar!!.setDisplayHomeAsUpEnabled(false)
    }

    /**
     * Refreshes the password list by re-executing the last navigation or search action.
     *
     * Use this rather than [resetPasswordList] after file system operations limited to the current
     * folder since it preserves the scroll position and navigation stack.
     */
    fun refreshPasswordList() {
        model.forceRefresh()
    }

    private val currentDir: File
        get() = plist?.currentDir ?: getRepositoryDirectory(applicationContext)

    private fun commitChange(message: String) {
        commitChange(this, message)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                // if we get here with a RESULT_OK then it's probably OK :)
                BaseGitActivity.REQUEST_CLONE -> settings.edit { putBoolean("repository_initialized", true) }
                // if went from decrypt->edit and user saved changes, we need to commitChange
                REQUEST_CODE_DECRYPT_AND_VERIFY -> {
                    if (data != null && data.getBooleanExtra("needCommit", false)) {
                        if (data.getStringExtra("OPERATION") == "EDIT") {
                            commitChange(resources.getString(R.string.git_commit_edit_text,
                                data.extras!!.getString("LONG_NAME")))
                        }
                    }
                    refreshPasswordList()
                }
                REQUEST_CODE_ENCRYPT -> {
                    commitChange(resources.getString(R.string.git_commit_add_text,
                        data!!.extras!!.getString("LONG_NAME")))
                    refreshPasswordList()
                }
                REQUEST_CODE_EDIT -> {
                    commitChange(resources.getString(R.string.git_commit_edit_text,
                        data!!.extras!!.getString("LONG_NAME")))
                    refreshPasswordList()
                }
                BaseGitActivity.REQUEST_INIT, NEW_REPO_BUTTON -> initializeRepositoryInfo()
                BaseGitActivity.REQUEST_SYNC, BaseGitActivity.REQUEST_PULL -> resetPasswordList()
                HOME -> checkLocalRepository()
                // duplicate code
                CLONE_REPO_BUTTON -> {
                    if (settings.getBoolean("git_external", false) &&
                        settings.getString("git_external_repo", null) != null) {
                        val externalRepoPath = settings.getString("git_external_repo", null)
                        val dir = externalRepoPath?.let { File(it) }
                        if (dir != null &&
                            dir.exists() &&
                            dir.isDirectory &&
                            !FileUtils.listFiles(dir, null, true).isEmpty() &&
                            getPasswords(dir, getRepositoryDirectory(this), sortOrder).isNotEmpty()) {
                            closeRepository()
                            checkLocalRepository()
                            return // if not empty, just show me the passwords!
                        }
                    }
                    val intent = Intent(activity, GitOperationActivity::class.java)
                    intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, BaseGitActivity.REQUEST_CLONE)
                    startActivityForResult(intent, BaseGitActivity.REQUEST_CLONE)
                }
                REQUEST_CODE_SELECT_FOLDER -> {
                    val intentData = data ?: return
                    tag(TAG).d {
                        "Moving passwords to ${intentData.getStringExtra("SELECTED_FOLDER_PATH")}"
                    }
                    tag(TAG).d {
                        TextUtils.join(", ", requireNotNull(intentData.getStringArrayListExtra("Files")))
                    }

                    val target = File(requireNotNull(intentData.getStringExtra("SELECTED_FOLDER_PATH")))
                    val repositoryPath = getRepositoryDirectory(applicationContext).absolutePath
                    if (!target.isDirectory) {
                        tag(TAG).e { "Tried moving passwords to a non-existing folder." }
                        return
                    }

                    // TODO move this to an async task
                    for (fileString in requireNotNull(intentData.getStringArrayListExtra("Files"))) {
                        val source = File(fileString)
                        if (!source.exists()) {
                            tag(TAG).e { "Tried moving something that appears non-existent." }
                            continue
                        }
                        val destinationFile = File(target.absolutePath + "/" + source.name)
                        val basename = FilenameUtils.getBaseName(source.absolutePath)
                        val sourceLongName = getLongName(requireNotNull(source.parent), repositoryPath, basename)
                        val destinationLongName = getLongName(target.absolutePath, repositoryPath, basename)
                        if (destinationFile.exists()) {
                            e { "Trying to move a file that already exists." }
                            MaterialAlertDialogBuilder(this)
                                .setTitle(resources.getString(R.string.password_exists_title))
                                .setMessage(resources.getString(
                                    R.string.password_exists_message,
                                    destinationLongName,
                                    sourceLongName)
                                )
                                .setPositiveButton(R.string.dialog_ok) { _, _ ->
                                    movePasswords(source, destinationFile, sourceLongName, destinationLongName)
                                }
                                .setNegativeButton(R.string.dialog_cancel, null)
                                .show()
                        } else {
                            movePasswords(source, destinationFile, sourceLongName, destinationLongName)
                        }
                    }
                    resetPasswordList()
                    if (plist != null) {
                        plist!!.dismissActionMode()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun movePasswords(source: File, destinationFile: File, sourceLongName: String, destinationLongName: String) {
        val sourceDestinationMap = if (source.isDirectory) {
            // Recursively list all files (not directories) below `source`, then
            // obtain the corresponding target file by resolving the relative path
            // starting at the destination folder.
            val sourceFiles = FileUtils.listFiles(source, null, true)
            sourceFiles.associateWith { destinationFile.resolve(it.relativeTo(source)) }
        } else {
            mapOf(source to destinationFile)
        }
        if (!source.renameTo(destinationFile)) {
            // TODO this should show a warning to the user
            e { "Something went wrong while moving." }
        } else {
            AutofillMatcher.updateMatches(this, sourceDestinationMap)
            commitChange(resources
                .getString(
                    R.string.git_commit_move_text,
                    sourceLongName,
                    destinationLongName))
        }
    }

    fun initRepository(operation: Int) {
        closeRepository()
        MaterialAlertDialogBuilder(this)
            .setTitle(resources.getString(R.string.location_dialog_title))
            .setMessage(resources.getString(R.string.location_dialog_text))
            .setPositiveButton(resources.getString(R.string.location_hidden)) { _, _ ->
                settings.edit { putBoolean("git_external", false) }
                when (operation) {
                    NEW_REPO_BUTTON -> initializeRepositoryInfo()
                    CLONE_REPO_BUTTON -> {
                        val intent = Intent(activity, GitServerConfigActivity::class.java)
                        intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, BaseGitActivity.REQUEST_CLONE)
                        startActivityForResult(intent, BaseGitActivity.REQUEST_CLONE)
                    }
                }
            }
            .setNegativeButton(resources.getString(R.string.location_sdcard)) { _, _ ->
                settings.edit { putBoolean("git_external", true) }
                val externalRepo = settings.getString("git_external_repo", null)
                if (externalRepo == null) {
                    val intent = Intent(activity, UserPreference::class.java)
                    intent.putExtra("operation", "git_external")
                    startActivityForResult(intent, operation)
                } else {
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(resources.getString(R.string.directory_selected_title))
                        .setMessage(resources.getString(R.string.directory_selected_message, externalRepo))
                        .setPositiveButton(resources.getString(R.string.use)) { _, _ ->
                            when (operation) {
                                NEW_REPO_BUTTON -> initializeRepositoryInfo()
                                CLONE_REPO_BUTTON -> {
                                    val intent = Intent(activity, GitServerConfigActivity::class.java)
                                    intent.putExtra(BaseGitActivity.REQUEST_ARG_OP, BaseGitActivity.REQUEST_CLONE)
                                    startActivityForResult(intent, BaseGitActivity.REQUEST_CLONE)
                                }
                            }
                        }
                        .setNegativeButton(resources.getString(R.string.change)) { _, _ ->
                            val intent = Intent(activity, UserPreference::class.java)
                            intent.putExtra("operation", "git_external")
                            startActivityForResult(intent, operation)
                        }
                        .show()
                }
            }
            .show()
    }

    fun matchPasswordWithApp(item: PasswordItem) {
        val path = item.file
            .absolutePath
            .replace(getRepositoryDirectory(applicationContext).toString() + "/", "")
            .replace(".gpg", "")
        val data = Intent()
        data.putExtra("path", path)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private val sortOrder: PasswordRepository.PasswordSortOrder
        get() = getSortOrder(settings)

    companion object {
        const val REQUEST_CODE_ENCRYPT = 9911
        const val REQUEST_CODE_DECRYPT_AND_VERIFY = 9913
        const val REQUEST_CODE_EDIT = 9916
        const val REQUEST_CODE_SELECT_FOLDER = 9917
        const val REQUEST_ARG_PATH = "PATH"
        private val TAG = PasswordStore::class.java.name
        const val CLONE_REPO_BUTTON = 401
        const val NEW_REPO_BUTTON = 402
        private const val HOME = 403
        private const val REQUEST_EXTERNAL_STORAGE = 50
        private fun isPrintable(c: Char): Boolean {
            val block = UnicodeBlock.of(c)
            return (!Character.isISOControl(c) &&
                block != null && block !== UnicodeBlock.SPECIALS)
        }

        private const val PREFERENCE_SEEN_AUTOFILL_ONBOARDING = "seen_autofill_onboarding"

        fun commitChange(activity: Activity, message: String, finishWithResultOnEnd: Intent? = null) {
            if (!PasswordRepository.isGitRepo()) {
                if (finishWithResultOnEnd != null) {
                    activity.setResult(Activity.RESULT_OK, finishWithResultOnEnd)
                    activity.finish()
                }
                return
            }
            object : GitOperation(getRepositoryDirectory(activity), activity) {
                override fun execute() {
                    tag(TAG).d { "Committing with message $message" }
                    val git = Git(repository)
                    val tasks = GitAsyncTask(activity, true, this, finishWithResultOnEnd)
                    tasks.execute(
                        git.add().addFilepattern("."),
                        git.commit().setAll(true).setMessage(message)
                    )
                }
            }.execute()
        }
    }
}
