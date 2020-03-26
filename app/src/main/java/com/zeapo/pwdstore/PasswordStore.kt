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
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.autofill.oreo.AutofillMatcher
import com.zeapo.pwdstore.crypto.PgpActivity
import com.zeapo.pwdstore.crypto.PgpActivity.Companion.getLongName
import com.zeapo.pwdstore.git.GitActivity
import com.zeapo.pwdstore.git.GitAsyncTask
import com.zeapo.pwdstore.git.GitOperation
import com.zeapo.pwdstore.ui.adapters.PasswordRecyclerAdapter
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
import java.io.File
import java.lang.Character.UnicodeBlock
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.revwalk.RevCommit
import timber.log.Timber

class PasswordStore : AppCompatActivity() {

    private lateinit var activity: PasswordStore
    private lateinit var searchItem: MenuItem
    private lateinit var searchView: SearchView
    private lateinit var settings: SharedPreferences
    private var plist: PasswordFragment? = null
    private var shortcutManager: ShortcutManager? = null

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
            shortcutManager = getSystemService(ShortcutManager::class.java)
        }

        // If user opens app with permission granted then revokes and returns,
        // prevent attempt to create password list fragment
        var savedInstance = savedInstanceState
        if (savedInstanceState != null && (!settings.getBoolean("git_external", false) ||
                        ContextCompat.checkSelfPermission(
                                activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED)) {
            savedInstance = null
        }
        super.onCreate(savedInstance)
        setContentView(R.layout.activity_pwdstore)
    }

    public override fun onResume() {
        super.onResume()
        // do not attempt to checkLocalRepository() if no storage permission: immediate crash
        if (settings.getBoolean("git_external", false)) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    val snack = Snackbar.make(
                                    findViewById(R.id.main_layout),
                                    getString(R.string.access_sdcard_text),
                                    Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.dialog_ok) {
                                ActivityCompat.requestPermissions(
                                        activity,
                                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                        REQUEST_EXTERNAL_STORAGE)
                            }
                    snack.show()
                    val view = snack.view
                    val tv: AppCompatTextView = view.findViewById(com.google.android.material.R.id.snackbar_text)
                    tv.setTextColor(Color.WHITE)
                    tv.maxLines = 10
                } else {
                    // No explanation needed, we can request the permission.
                    ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                            REQUEST_EXTERNAL_STORAGE)
                }
            } else {
                checkLocalRepository()
            }
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
        // If request is cancelled, the result arrays are empty.
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkLocalRepository()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_menu, menu)
        searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(
                object : OnQueryTextListener {
                    override fun onQueryTextSubmit(s: String): Boolean {
                        return true
                    }

                    override fun onQueryTextChange(s: String): Boolean {
                        filterListAdapter(s)
                        return true
                    }
                })

        // When using the support library, the setOnActionExpandListener() method is
        // static and accepts the MenuItem object as an argument
        searchItem.setOnActionExpandListener(
                object : OnActionExpandListener {
                    override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                        refreshListAdapter()
                        return true
                    }

                    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                        return true
                    }
                })
        if (settings.getBoolean("search_on_start", false)) {
            searchItem.expandActionView()
        }
        return super.onCreateOptionsMenu(menu)
    }

    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        val intent: Intent
        val initBefore = MaterialAlertDialogBuilder(this)
                .setMessage(this.resources.getString(R.string.creation_dialog_text))
                .setPositiveButton(this.resources.getString(R.string.dialog_ok), null)
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
                intent = Intent(this, GitActivity::class.java)
                intent.putExtra("Operation", GitActivity.REQUEST_PUSH)
                startActivityForResult(intent, GitActivity.REQUEST_PUSH)
                return true
            }
            R.id.git_pull -> {
                if (!isInitialized) {
                    initBefore.show()
                    return false
                }
                intent = Intent(this, GitActivity::class.java)
                intent.putExtra("Operation", GitActivity.REQUEST_PULL)
                startActivityForResult(intent, GitActivity.REQUEST_PULL)
                return true
            }
            R.id.git_sync -> {
                if (!isInitialized) {
                    initBefore.show()
                    return false
                }
                intent = Intent(this, GitActivity::class.java)
                intent.putExtra("Operation", GitActivity.REQUEST_SYNC)
                startActivityForResult(intent, GitActivity.REQUEST_SYNC)
                return true
            }
            R.id.refresh -> {
                updateListAdapter()
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

    fun openSettings(view: View?) {
        val intent: Intent
        try {
            intent = Intent(this, UserPreference::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cloneExistingRepository(view: View?) {
        initRepository(CLONE_REPO_BUTTON)
    }

    fun createNewRepository(view: View?) {
        initRepository(NEW_REPO_BUTTON)
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
                settings.edit().putBoolean("repository_initialized", true).apply()
            } else {
                throw IllegalStateException("Failed to initialize repository state.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (!localDir.delete()) {
                Timber.tag(TAG).d("Failed to delete local repository")
            }
            return
        }
        checkLocalRepository()
    }

    private fun initializeRepositoryInfo() {
        val externalRepoPath = settings.getString("git_external_repo", null)
        if (settings.getBoolean("git_external", false) && externalRepoPath != null) {
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
                    .setMessage(this.resources.getString(R.string.key_dialog_text))
                    .setPositiveButton(this.resources.getString(R.string.dialog_positive)) { _, _ ->
                        val intent = Intent(activity, UserPreference::class.java)
                        startActivityForResult(intent, GitActivity.REQUEST_INIT)
                    }
                    .setNegativeButton(this.resources.getString(R.string.dialog_negative), null)
                    .show()
        }
        createRepository()
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
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        if (localDir != null && settings.getBoolean("repository_initialized", false)) {
            Timber.tag(TAG).d("Check, dir: ${localDir.absolutePath}")
            // do not push the fragment if we already have it
            if (fragmentManager.findFragmentByTag("PasswordsList") == null ||
                    settings.getBoolean("repo_changed", false)) {
                settings.edit().putBoolean("repo_changed", false).apply()
                plist = PasswordFragment()
                val args = Bundle()
                args.putString("Path", getRepositoryDirectory(applicationContext).absolutePath)

                // if the activity was started from the autofill settings, the
                // intent is to match a clicked pwd with app. pass this to fragment
                if (intent.getBooleanExtra("matchWith", false)) {
                    args.putBoolean("matchWith", true)
                }
                plist!!.arguments = args
                supportActionBar!!.show()
                supportActionBar!!.setDisplayHomeAsUpEnabled(false)
                fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                fragmentTransaction.replace(R.id.main_layout, plist!!, "PasswordsList")
                fragmentTransaction.commit()
            }
        } else {
            supportActionBar!!.hide()
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            val cloneFrag = ToCloneOrNot()
            fragmentTransaction.replace(R.id.main_layout, cloneFrag, "ToCloneOrNot")
            fragmentTransaction.commit()
        }
    }

    override fun onBackPressed() {
        if (null != plist && plist!!.isNotEmpty) {
            plist!!.popBack()
        } else {
            super.onBackPressed()
        }
        if (null != plist && !plist!!.isNotEmpty) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        }
    }

    private fun getRelativePath(fullPath: String, repositoryPath: String): String {
        return fullPath.replace(repositoryPath, "").replace("/+".toRegex(), "/")
    }

    private fun getLastChangedTimestamp(fullPath: String): Long {
        val repoPath = getRepositoryDirectory(this)
        val repository = getRepository(repoPath)
        if (repository == null) {
            Timber.tag(TAG).d("getLastChangedTimestamp: No git repository")
            return File(fullPath).lastModified()
        }
        val git = Git(repository)
        val relativePath = getRelativePath(fullPath, repoPath.absolutePath).substring(1) // Removes leading '/'
        val iterator: Iterator<RevCommit>
        iterator = try {
            git.log().addPath(relativePath).call().iterator()
        } catch (e: GitAPIException) {
            Timber.tag(TAG).e(e, "getLastChangedTimestamp: GITAPIException")
            return -1
        }
        if (!iterator.hasNext()) {
            Timber.tag(TAG).w("getLastChangedTimestamp: No commits for file: $relativePath")
            return -1
        }
        return iterator.next().commitTime.toLong() * 1000
    }

    fun decryptPassword(item: PasswordItem) {
        val decryptIntent = Intent(this, PgpActivity::class.java)
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
        intent.putExtra("PARENT_PATH", currentDir!!.absolutePath)
        intent.putExtra("REPO_PATH", getRepositoryDirectory(applicationContext).absolutePath)
        intent.putExtra("OPERATION", "EDIT")
        startActivityForResult(intent, REQUEST_CODE_EDIT)
    }

    private fun validateState(): Boolean {
        if (!isInitialized) {
            MaterialAlertDialogBuilder(this)
                    .setMessage(this.resources.getString(R.string.creation_dialog_text))
                    .setPositiveButton(this.resources.getString(R.string.dialog_ok), null)
                    .show()
            return false
        }
        if (settings.getStringSet("openpgp_key_ids_set", HashSet()).isNullOrEmpty()) {
            MaterialAlertDialogBuilder(this)
                    .setTitle(this.resources.getString(R.string.no_key_selected_dialog_title))
                    .setMessage(this.resources.getString(R.string.no_key_selected_dialog_text))
                    .setPositiveButton(this.resources.getString(R.string.dialog_ok)) { _, _ ->
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
        Timber.tag(TAG).i("Adding file to : ${currentDir!!.absolutePath}")
        val intent = Intent(this, PgpActivity::class.java)
        intent.putExtra("FILE_PATH", currentDir.absolutePath)
        intent.putExtra("REPO_PATH", getRepositoryDirectory(applicationContext).absolutePath)
        intent.putExtra("OPERATION", "ENCRYPT")
        startActivityForResult(intent, REQUEST_CODE_ENCRYPT)
    }

    fun createFolder() {
        if (!validateState()) return
        FolderCreationDialogFragment.newInstance(currentDir!!.path).show(supportFragmentManager, null)
    }

    // deletes passwords in order from top to bottom
    fun deletePasswords(adapter: PasswordRecyclerAdapter, selectedItems: MutableSet<Int>) {
        val it: MutableIterator<*> = selectedItems.iterator()
        if (!it.hasNext()) {
            return
        }
        val position = it.next() as Int
        val item = adapter.values[position]
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
                    adapter.remove(position)
                    it.remove()
                    adapter.updateSelectedItems(position, selectedItems)
                    commitChange(resources.getString(R.string.git_commit_remove_text, item.longName))
                    deletePasswords(adapter, selectedItems)
                }
                .setNegativeButton(this.resources.getString(R.string.dialog_no)) { _, _ ->
                    it.remove()
                    deletePasswords(adapter, selectedItems)
                }
                .show()
    }

    fun movePasswords(values: ArrayList<PasswordItem>) {
        val intent = Intent(this, SelectFolderActivity::class.java)
        val fileLocations = ArrayList<String>()
        for ((_, _, _, file) in values) {
            fileLocations.add(file.absolutePath)
        }
        intent.putExtra("Files", fileLocations)
        intent.putExtra("Operation", "SELECTFOLDER")
        startActivityForResult(intent, REQUEST_CODE_SELECT_FOLDER)
    }

    /** clears adapter's content and updates it with a fresh list of passwords from the root  */
    fun updateListAdapter() {
        plist?.updateAdapter()
    }

    /** Updates the adapter with the current view of passwords  */
    private fun refreshListAdapter() {
        plist?.refreshAdapter()
    }

    private fun filterListAdapter(filter: String) {
        plist?.filterAdapter(filter)
    }

    private val currentDir: File?
        get() = plist?.currentDir ?: getRepositoryDirectory(applicationContext)

    private fun commitChange(message: String) {
        Companion.commitChange(activity, message)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                // if we get here with a RESULT_OK then it's probably OK :)
                GitActivity.REQUEST_CLONE -> settings.edit().putBoolean("repository_initialized", true).apply()
                // if went from decrypt->edit and user saved changes or HOTP counter was
                // incremented, we need to commitChange
                REQUEST_CODE_DECRYPT_AND_VERIFY -> {
                    if (data != null && data.getBooleanExtra("needCommit", false)) {
                        if (data.getStringExtra("OPERATION") == "EDIT") {
                            commitChange(this.resources
                                    .getString(
                                            R.string.git_commit_edit_text,
                                            data.extras!!.getString("LONG_NAME")))
                        } else {
                            commitChange(this.resources
                                    .getString(
                                            R.string.git_commit_increment_text,
                                            data.extras!!.getString("LONG_NAME")))
                        }
                    }
                    refreshListAdapter()
                }
                REQUEST_CODE_ENCRYPT -> {
                    commitChange(this.resources
                            .getString(
                                    R.string.git_commit_add_text,
                                    data!!.extras!!.getString("LONG_NAME")))
                    refreshListAdapter()
                }
                REQUEST_CODE_EDIT -> {
                    commitChange(
                            this.resources
                                    .getString(
                                            R.string.git_commit_edit_text,
                                            data!!.extras!!.getString("LONG_NAME")))
                    refreshListAdapter()
                }
                GitActivity.REQUEST_INIT, NEW_REPO_BUTTON -> initializeRepositoryInfo()
                GitActivity.REQUEST_SYNC, GitActivity.REQUEST_PULL -> updateListAdapter()
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
                    val intent = Intent(activity, GitActivity::class.java)
                    intent.putExtra("Operation", GitActivity.REQUEST_CLONE)
                    startActivityForResult(intent, GitActivity.REQUEST_CLONE)
                }
                REQUEST_CODE_SELECT_FOLDER -> {
                    Timber.tag(TAG)
                            .d("Moving passwords to ${data!!.getStringExtra("SELECTED_FOLDER_PATH")}")
                    Timber.tag(TAG).d(
                            TextUtils.join(", ", requireNotNull(data.getStringArrayListExtra("Files")))
                    )

                    val target = File(requireNotNull(data.getStringExtra("SELECTED_FOLDER_PATH")))
                    val repositoryPath = getRepositoryDirectory(applicationContext).absolutePath
                    if (!target.isDirectory) {
                        Timber.tag(TAG).e("Tried moving passwords to a non-existing folder.")
                        return
                    }

                    // TODO move this to an async task
                    for (fileString in requireNotNull(data.getStringArrayListExtra("Files"))) {
                        val source = File(fileString)
                        if (!source.exists()) {
                            Timber.tag(TAG).e("Tried moving something that appears non-existent.")
                            continue
                        }
                        val destinationFile = File(target.absolutePath + "/" + source.name)
                        val basename = FilenameUtils.getBaseName(source.absolutePath)
                        val sourceLongName = getLongName(requireNotNull(source.parent), repositoryPath, basename)
                        val destinationLongName = getLongName(target.absolutePath, repositoryPath, basename)
                        if (destinationFile.exists()) {
                            Timber.tag(TAG).e("Trying to move a file that already exists.")
                            // TODO: Add option to cancel overwrite. Will be easier once this is an async task.
                            MaterialAlertDialogBuilder(this)
                                    .setTitle(resources.getString(R.string.password_exists_title))
                                    .setMessage(resources
                                            .getString(
                                                    R.string.password_exists_message,
                                                    destinationLongName,
                                                    sourceLongName))
                                    .setPositiveButton("Okay", null)
                                    .show()
                        }
                        val sourceDestinationMap = if (source.isDirectory) {
                            check(destinationFile.isDirectory) { "Moving a directory to a file" }
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
                            Timber.tag(TAG).e("Something went wrong while moving.")
                        } else {
                            AutofillMatcher.updateMatches(this, sourceDestinationMap)
                            commitChange(this.resources
                                    .getString(
                                            R.string.git_commit_move_text,
                                            sourceLongName,
                                            destinationLongName))
                        }
                    }
                    updateListAdapter()
                    if (plist != null) {
                        plist!!.dismissActionMode()
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun initRepository(operation: Int) {
        closeRepository()
        MaterialAlertDialogBuilder(this)
                .setTitle(this.resources.getString(R.string.location_dialog_title))
                .setMessage(this.resources.getString(R.string.location_dialog_text))
                .setPositiveButton(this.resources.getString(R.string.location_hidden)) { _, _ ->
                    settings.edit().putBoolean("git_external", false).apply()
                    when (operation) {
                        NEW_REPO_BUTTON -> initializeRepositoryInfo()
                        CLONE_REPO_BUTTON -> {
                            initialize(this@PasswordStore)
                            val intent = Intent(activity, GitActivity::class.java)
                            intent.putExtra("Operation", GitActivity.REQUEST_CLONE)
                            startActivityForResult(intent, GitActivity.REQUEST_CLONE)
                        }
                    }
                }
                .setNegativeButton(this.resources.getString(R.string.location_sdcard)) { _, _ ->
                    settings.edit().putBoolean("git_external", true).apply()
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
                                            initialize(this@PasswordStore)
                                            val intent = Intent(activity, GitActivity::class.java)
                                            intent.putExtra("Operation", GitActivity.REQUEST_CLONE)
                                            startActivityForResult(intent, GitActivity.REQUEST_CLONE)
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
        const val REQUEST_CODE_SIGN = 9910
        const val REQUEST_CODE_ENCRYPT = 9911
        const val REQUEST_CODE_SIGN_AND_ENCRYPT = 9912
        const val REQUEST_CODE_DECRYPT_AND_VERIFY = 9913
        const val REQUEST_CODE_GET_KEY = 9914
        const val REQUEST_CODE_GET_KEY_IDS = 9915
        const val REQUEST_CODE_EDIT = 9916
        const val REQUEST_CODE_SELECT_FOLDER = 9917
        private val TAG = PasswordStore::class.java.name
        private const val CLONE_REPO_BUTTON = 401
        private const val NEW_REPO_BUTTON = 402
        private const val HOME = 403
        private const val REQUEST_EXTERNAL_STORAGE = 50
        private fun isPrintable(c: Char): Boolean {
            val block = UnicodeBlock.of(c)
            return (!Character.isISOControl(c) &&
                    block != null && block !== UnicodeBlock.SPECIALS)
        }

        fun commitChange(activity: Activity, message: String) {
            object : GitOperation(getRepositoryDirectory(activity), activity) {
                override fun execute() {
                    Timber.tag(TAG).d("Committing with message $message")
                    val git = Git(repository)
                    val tasks = GitAsyncTask(activity, false, true, this)
                    tasks.execute(
                        git.add().addFilepattern("."),
                        git.commit().setAll(true).setMessage(message)
                    )
                }
            }.execute()
        }
    }
}
