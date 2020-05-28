/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.view.MenuItem
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.biometric.BiometricManager
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.github.ajalt.timberkt.Timber.tag
import com.github.ajalt.timberkt.d
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zeapo.pwdstore.autofill.AutofillPreferenceActivity
import com.zeapo.pwdstore.autofill.oreo.BrowserAutofillSupportLevel
import com.zeapo.pwdstore.autofill.oreo.getInstalledBrowsersWithAutofillSupportLevel
import com.zeapo.pwdstore.crypto.PgpActivity
import com.zeapo.pwdstore.git.GitConfigActivity
import com.zeapo.pwdstore.git.GitServerConfigActivity
import com.zeapo.pwdstore.pwgenxkpwd.XkpwdDictionary
import com.zeapo.pwdstore.sshkeygen.ShowSshKeyFragment
import com.zeapo.pwdstore.sshkeygen.SshKeyGenActivity
import com.zeapo.pwdstore.utils.BiometricAuthenticator
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.autofillManager
import com.zeapo.pwdstore.utils.getEncryptedPrefs
import me.msfjarvis.openpgpktx.util.OpenPgpUtils
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.HashSet
import java.util.TimeZone

typealias ClickListener = Preference.OnPreferenceClickListener
typealias ChangeListener = Preference.OnPreferenceChangeListener

class UserPreference : AppCompatActivity() {

    private lateinit var prefsFragment: PrefsFragment

    class PrefsFragment : PreferenceFragmentCompat() {
        private var autoFillEnablePreference: SwitchPreferenceCompat? = null
        private var clearSavedPassPreference: Preference? = null
        private lateinit var autofillDependencies: List<Preference>
        private lateinit var oreoAutofillDependencies: List<Preference>
        private lateinit var callingActivity: UserPreference
        private lateinit var encryptedPreferences: SharedPreferences

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            callingActivity = requireActivity() as UserPreference
            val context = requireContext()
            val sharedPreferences = preferenceManager.sharedPreferences
            encryptedPreferences = requireActivity().applicationContext.getEncryptedPrefs("git_operation")

            addPreferencesFromResource(R.xml.preference)

            // Git preferences
            val gitServerPreference = findPreference<Preference>("git_server_info")
            val openkeystoreIdPreference = findPreference<Preference>("ssh_openkeystore_clear_keyid")
            val gitConfigPreference = findPreference<Preference>("git_config")
            val sshKeyPreference = findPreference<Preference>("ssh_key")
            val sshKeygenPreference = findPreference<Preference>("ssh_keygen")
            clearSavedPassPreference = findPreference("clear_saved_pass")
            val viewSshKeyPreference = findPreference<Preference>("ssh_see_key")
            val deleteRepoPreference = findPreference<Preference>("git_delete_repo")
            val externalGitRepositoryPreference = findPreference<Preference>("git_external")
            val selectExternalGitRepositoryPreference = findPreference<Preference>("pref_select_external")

            if (!PasswordRepository.isGitRepo()) {
                listOfNotNull(
                    gitServerPreference, gitConfigPreference, sshKeyPreference,
                    sshKeygenPreference, viewSshKeyPreference, clearSavedPassPreference
                ).forEach {
                    it.parent?.removePreference(it)
                }
            }

            // Crypto preferences
            val keyPreference = findPreference<Preference>("openpgp_key_id_pref")

            // General preferences
            val showTimePreference = findPreference<Preference>("general_show_time")
            val clearClipboard20xPreference = findPreference<CheckBoxPreference>("clear_clipboard_20x")

            // Autofill preferences
            autoFillEnablePreference = findPreference("autofill_enable")
            val oreoAutofillDirectoryStructurePreference = findPreference<ListPreference>("oreo_autofill_directory_structure")
            val oreoAutofillDefaultUsername = findPreference<EditTextPreference>("oreo_autofill_default_username")
            val autoFillAppsPreference = findPreference<Preference>("autofill_apps")
            val autoFillDefaultPreference = findPreference<CheckBoxPreference>("autofill_default")
            val autoFillAlwaysShowDialogPreference = findPreference<CheckBoxPreference>("autofill_always")
            val autoFillShowFullNamePreference = findPreference<CheckBoxPreference>("autofill_full_path")
            autofillDependencies = listOfNotNull(
                autoFillAppsPreference,
                autoFillDefaultPreference,
                autoFillAlwaysShowDialogPreference,
                autoFillShowFullNamePreference
            )
            oreoAutofillDependencies = listOfNotNull(
                oreoAutofillDirectoryStructurePreference,
                oreoAutofillDefaultUsername
            )

            // Misc preferences
            val appVersionPreference = findPreference<Preference>("app_version")

            selectExternalGitRepositoryPreference?.summary = sharedPreferences.getString("git_external_repo", getString(R.string.no_repo_selected))
            viewSshKeyPreference?.isVisible = sharedPreferences.getBoolean("use_generated_key", false)
            deleteRepoPreference?.isVisible = !sharedPreferences.getBoolean("git_external", false)
            clearClipboard20xPreference?.isVisible = sharedPreferences.getString("general_show_time", "45")?.toInt() != 0
            val selectedKeys = (sharedPreferences.getStringSet("openpgp_key_ids_set", null)
                ?: HashSet()).toTypedArray()
            keyPreference?.summary = if (selectedKeys.isEmpty()) {
                this.resources.getString(R.string.pref_no_key_selected)
            } else {
                selectedKeys.joinToString(separator = ";") { s ->
                    OpenPgpUtils.convertKeyIdToHex(java.lang.Long.valueOf(s))
                }
            }
            openkeystoreIdPreference?.isVisible = sharedPreferences.getString("ssh_openkeystore_keyid", null)?.isNotEmpty()
                ?: false

            updateAutofillSettings()
            updateClearSavedPassphrasePrefs()

            appVersionPreference?.summary = "Version: ${BuildConfig.VERSION_NAME}"

            keyPreference?.onPreferenceClickListener = ClickListener {
                val providerPackageName = requireNotNull(sharedPreferences.getString("openpgp_provider_list", ""))
                if (providerPackageName.isEmpty()) {
                    Snackbar.make(requireView(), resources.getString(R.string.provider_toast_text), Snackbar.LENGTH_LONG).show()
                    false
                } else {
                    val intent = Intent(callingActivity, PgpActivity::class.java)
                    intent.putExtra("OPERATION", "GET_KEY_ID")
                    startActivityForResult(intent, IMPORT_PGP_KEY)
                    true
                }
            }

            sshKeyPreference?.onPreferenceClickListener = ClickListener {
                callingActivity.getSshKey()
                true
            }

            sshKeygenPreference?.onPreferenceClickListener = ClickListener {
                callingActivity.makeSshKey(true)
                true
            }

            viewSshKeyPreference?.onPreferenceClickListener = ClickListener {
                val df = ShowSshKeyFragment()
                df.show(parentFragmentManager, "public_key")
                true
            }

            clearSavedPassPreference?.onPreferenceClickListener = ClickListener {
                encryptedPreferences.edit {
                    if (encryptedPreferences.getString("https_password", null) != null)
                        remove("https_password")
                    else if (encryptedPreferences.getString("ssh_key_local_passphrase", null) != null)
                        remove("ssh_key_local_passphrase")
                }
                updateClearSavedPassphrasePrefs()
                true
            }

            openkeystoreIdPreference?.onPreferenceClickListener = ClickListener {
                sharedPreferences.edit { putString("ssh_openkeystore_keyid", null) }
                it.isVisible = false
                true
            }

            gitServerPreference?.onPreferenceClickListener = ClickListener {
                startActivity(Intent(callingActivity, GitServerConfigActivity::class.java))
                true
            }

            gitConfigPreference?.onPreferenceClickListener = ClickListener {
                startActivity(Intent(callingActivity, GitConfigActivity::class.java))
                true
            }

            deleteRepoPreference?.onPreferenceClickListener = ClickListener {
                val repoDir = PasswordRepository.getRepositoryDirectory(callingActivity.applicationContext)
                MaterialAlertDialogBuilder(callingActivity)
                    .setTitle(R.string.pref_dialog_delete_title)
                    .setMessage(resources.getString(R.string.dialog_delete_msg, repoDir))
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_delete) { dialogInterface, _ ->
                        try {
                            FileUtils.cleanDirectory(PasswordRepository.getRepositoryDirectory(callingActivity.applicationContext))
                            PasswordRepository.closeRepository()
                        } catch (ignored: Exception) {
                            // TODO Handle the different cases of exceptions
                        }

                        sharedPreferences.edit { putBoolean("repository_initialized", false) }
                        dialogInterface.cancel()
                        callingActivity.finish()
                    }
                    .setNegativeButton(R.string.dialog_do_not_delete) { dialogInterface, _ -> run { dialogInterface.cancel() } }
                    .show()

                true
            }

            selectExternalGitRepositoryPreference?.summary =
                sharedPreferences.getString("git_external_repo", context.getString(R.string.no_repo_selected))
            selectExternalGitRepositoryPreference?.onPreferenceClickListener = ClickListener {
                callingActivity.selectExternalGitRepository()
                true
            }

            val resetRepo = Preference.OnPreferenceChangeListener { _, o ->
                deleteRepoPreference?.isVisible = !(o as Boolean)
                PasswordRepository.closeRepository()
                sharedPreferences.edit { putBoolean("repo_changed", true) }
                true
            }

            selectExternalGitRepositoryPreference?.onPreferenceChangeListener = resetRepo
            externalGitRepositoryPreference?.onPreferenceChangeListener = resetRepo

            autoFillAppsPreference?.onPreferenceClickListener = ClickListener {
                val intent = Intent(callingActivity, AutofillPreferenceActivity::class.java)
                startActivity(intent)
                true
            }

            autoFillEnablePreference?.onPreferenceClickListener = ClickListener {
                onEnableAutofillClick()
                true
            }

            findPreference<Preference>("export_passwords")?.apply {
                isVisible = sharedPreferences.getBoolean("repository_initialized", false)
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    callingActivity.exportPasswords()
                    true
                }
            }

            showTimePreference?.onPreferenceChangeListener = ChangeListener { _, newValue: Any? ->
                try {
                    val isEnabled = newValue.toString().toInt() != 0
                    clearClipboard20xPreference?.isVisible = isEnabled
                    true
                } catch (e: NumberFormatException) {
                    false
                }
            }

            showTimePreference?.summaryProvider = Preference.SummaryProvider<Preference> {
                getString(R.string.pref_clipboard_timeout_summary, sharedPreferences.getString("general_show_time", "45"))
            }

            findPreference<CheckBoxPreference>("enable_debug_logging")?.isVisible = !BuildConfig.ENABLE_DEBUG_FEATURES

            findPreference<CheckBoxPreference>("biometric_auth")?.apply {
                val isFingerprintSupported = BiometricManager.from(requireContext()).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
                if (!isFingerprintSupported) {
                    isEnabled = false
                    isChecked = false
                    summary = getString(R.string.biometric_auth_summary_error)
                } else {
                    setOnPreferenceClickListener {
                        isEnabled = false
                        sharedPreferences.edit {
                            val checked = isChecked
                            BiometricAuthenticator.authenticate(requireActivity()) { result ->
                                when (result) {
                                    is BiometricAuthenticator.Result.Success -> {
                                        // Apply the changes
                                        putBoolean("biometric_auth", checked)
                                        isEnabled = true
                                    }
                                    else -> {
                                        // If any error occurs, revert back to the previous state. This
                                        // catch-all clause includes the cancellation case.
                                        putBoolean("biometric_auth", !checked)
                                        isChecked = !checked
                                        isEnabled = true
                                    }
                                }
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                                requireContext().getSystemService<ShortcutManager>()?.apply {
                                    removeDynamicShortcuts(dynamicShortcuts.map { it.id }.toMutableList())
                                }
                            }
                        }
                        true
                    }
                }
            }

            val prefCustomXkpwdDictionary = findPreference<Preference>("pref_key_custom_dict")
            prefCustomXkpwdDictionary?.onPreferenceClickListener = ClickListener {
                callingActivity.storeCustomDictionaryPath()
                true
            }
            val dictUri = sharedPreferences.getString("pref_key_custom_dict", "")

            if (!TextUtils.isEmpty(dictUri)) {
                setCustomDictSummary(prefCustomXkpwdDictionary, Uri.parse(dictUri))
            }

            val prefIsCustomDict = findPreference<CheckBoxPreference>("pref_key_is_custom_dict")
            val prefCustomDictPicker = findPreference<Preference>("pref_key_custom_dict")
            val prefPwgenType = findPreference<ListPreference>("pref_key_pwgen_type")
            updateXkPasswdPrefsVisibility(prefPwgenType?.value, prefIsCustomDict, prefCustomDictPicker)

            prefPwgenType?.onPreferenceChangeListener = ChangeListener { _, newValue ->
                updateXkPasswdPrefsVisibility(newValue, prefIsCustomDict, prefCustomDictPicker)
                true
            }

            prefIsCustomDict?.onPreferenceChangeListener = ChangeListener { _, newValue ->
                if (!(newValue as Boolean)) {
                    val customDictFile = File(context.filesDir, XkpwdDictionary.XKPWD_CUSTOM_DICT_FILE)
                    if (customDictFile.exists()) {
                        FileUtils.deleteQuietly(customDictFile)
                    }
                    prefCustomDictPicker?.setSummary(R.string.xkpwgen_pref_custom_dict_picker_summary)
                }
                true
            }
        }

        private fun updateXkPasswdPrefsVisibility(newValue: Any?, prefIsCustomDict: CheckBoxPreference?, prefCustomDictPicker: Preference?) {
            when (newValue as String) {
                PgpActivity.KEY_PWGEN_TYPE_CLASSIC -> {
                    prefIsCustomDict?.isVisible = false
                    prefCustomDictPicker?.isVisible = false
                }
                PgpActivity.KEY_PWGEN_TYPE_XKPASSWD -> {
                    prefIsCustomDict?.isVisible = true
                    prefCustomDictPicker?.isVisible = true
                }
            }
        }

        private fun updateAutofillSettings() {
            val isAccessibilityServiceEnabled = callingActivity.isAccessibilityServiceEnabled
            val isAutofillServiceEnabled = callingActivity.isAutofillServiceEnabled
            autoFillEnablePreference?.isChecked =
                isAccessibilityServiceEnabled || isAutofillServiceEnabled
            autofillDependencies.forEach {
                it.isVisible = isAccessibilityServiceEnabled
            }
            oreoAutofillDependencies.forEach {
                it.isVisible = isAutofillServiceEnabled
            }
        }

        private fun updateClearSavedPassphrasePrefs() {
            clearSavedPassPreference?.apply {
                val sshPass = encryptedPreferences.getString("ssh_key_local_passphrase", null)
                val httpsPass = encryptedPreferences.getString("https_password", null)
                if (sshPass == null && httpsPass == null) {
                    isVisible = false
                    return@apply
                }
                title = when {
                    httpsPass != null -> getString(R.string.clear_saved_passphrase_https)
                    sshPass != null -> getString(R.string.clear_saved_passphrase_ssh)
                    else -> null
                }
                isVisible = true
            }
        }

        private fun onEnableAutofillClick() {
            if (callingActivity.isAccessibilityServiceEnabled) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else if (callingActivity.isAutofillServiceEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    callingActivity.autofillManager!!.disableAutofillServices()
                else
                    throw IllegalStateException("isAutofillServiceEnabled == true, but Build.VERSION.SDK_INT < Build.VERSION_CODES.O")
            } else {
                val enableOreoAutofill = callingActivity.isAutofillServiceSupported
                MaterialAlertDialogBuilder(callingActivity).run {
                    setTitle(R.string.pref_autofill_enable_title)
                    if (enableOreoAutofill && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        @SuppressLint("InflateParams")
                        val layout =
                            layoutInflater.inflate(R.layout.oreo_autofill_instructions, null)
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
                    } else {
                        setView(R.layout.autofill_instructions)
                    }
                    setPositiveButton(R.string.dialog_ok) { _, _ ->
                        val intent =
                            if (enableOreoAutofill && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                    data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                                }
                            } else {
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            }
                        startActivity(intent)
                    }
                    setNegativeButton(R.string.dialog_cancel, null)
                    setOnDismissListener { updateAutofillSettings() }
                    show()
                }
            }
        }

        override fun onResume() {
            super.onResume()
            updateAutofillSettings()
            updateClearSavedPassphrasePrefs()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setResult(Activity.RESULT_OK)
        finish()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.getStringExtra("operation")) {
            "get_ssh_key" -> getSshKey()
            "make_ssh_key" -> makeSshKey(false)
            "git_external" -> selectExternalGitRepository()
        }
        prefsFragment = PrefsFragment()

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, prefsFragment)
            .commit()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun selectExternalGitRepository() {
        MaterialAlertDialogBuilder(this)
            .setTitle(this.resources.getString(R.string.external_repository_dialog_title))
            .setMessage(this.resources.getString(R.string.external_repository_dialog_text))
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(Intent.createChooser(i, "Choose Directory"), SELECT_GIT_DIRECTORY)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            android.R.id.home -> {
                setResult(Activity.RESULT_OK)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Opens a file explorer to import the private key
     */
    private fun getSshKey() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, IMPORT_SSH_KEY)
    }

    /**
     * Exports the passwords
     */
    private fun exportPasswords() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(Intent.createChooser(i, "Choose Directory"), EXPORT_PASSWORDS)
    }

    /**
     * Opens a key generator to generate a public/private key pair
     */
    fun makeSshKey(fromPreferences: Boolean) {
        val intent = Intent(applicationContext, SshKeyGenActivity::class.java)
        startActivity(intent)
        if (!fromPreferences) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    /**
     * Pick custom xkpwd dictionary from sdcard
     */
    private fun storeCustomDictionaryPath() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, SET_CUSTOM_XKPWD_DICT)
    }

    @Throws(IllegalArgumentException::class, IOException::class)
    private fun copySshKey(uri: Uri) {
        // First check whether the content at uri is likely an SSH private key.
        val fileSize = contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                // Cursor returns only a single row.
                cursor.moveToFirst()
                cursor.getInt(0)
            } ?: throw IOException(getString(R.string.ssh_key_does_not_exist))

        // We assume that an SSH key's ideal size is > 0 bytes && < 100 kilobytes.
        if (fileSize > 100_000 || fileSize == 0)
            throw IllegalArgumentException(getString(R.string.ssh_key_import_error_not_an_ssh_key_message))

        val sshKeyInputStream = contentResolver.openInputStream(uri)
            ?: throw IOException(getString(R.string.ssh_key_does_not_exist))
        val lines = sshKeyInputStream.bufferedReader().readLines()

        // The file must have more than 2 lines, and the first and last line must have private key
        // markers.
        if (lines.size < 2 ||
            !Regex("BEGIN .* PRIVATE KEY").containsMatchIn(lines.first()) ||
            !Regex("END .* PRIVATE KEY").containsMatchIn(lines.last())
        )
            throw IllegalArgumentException(getString(R.string.ssh_key_import_error_not_an_ssh_key_message))

        // Canonicalize line endings to '\n'.
        File("$filesDir/.ssh_key").writeText(lines.joinToString("\n"))
    }

    private val isAccessibilityServiceEnabled: Boolean
        get() {
            val am = getSystemService<AccessibilityManager>() ?: return false
            val runningServices = am
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            return runningServices
                .map { it.id.substringBefore("/") }
                .any { it == BuildConfig.APPLICATION_ID }
        }

    private val isAutofillServiceSupported: Boolean
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return autofillManager?.isAutofillSupported != null
        }

    private val isAutofillServiceEnabled: Boolean
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            return autofillManager?.hasEnabledAutofillServices() == true
        }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (resultCode == Activity.RESULT_OK) {
            if (data == null) {
                setResult(Activity.RESULT_CANCELED)
                return
            }

            when (requestCode) {
                IMPORT_SSH_KEY -> {
                    try {
                        val uri: Uri = data.data ?: throw IOException("Unable to open file")

                        copySshKey(uri)

                        Toast.makeText(
                            this,
                            this.resources.getString(R.string.ssh_key_success_dialog_title),
                            Toast.LENGTH_LONG
                        ).show()
                        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                        prefs.edit { putBoolean("use_generated_key", false) }

                        // Delete the public key from generation
                        File("""$filesDir/.ssh_key.pub""").delete()
                        setResult(Activity.RESULT_OK)

                        finish()
                    } catch (e: Exception) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(resources.getString(R.string.ssh_key_error_dialog_title))
                            .setMessage(e.message)
                            .setPositiveButton(resources.getString(R.string.dialog_ok), null)
                            .show()
                    }
                }
                EDIT_GIT_INFO -> {
                }
                SELECT_GIT_DIRECTORY -> {
                    val uri = data.data

                    tag(TAG).d { "Selected repository URI is $uri" }
                    // TODO: This is fragile. Workaround until PasswordItem is backed by DocumentFile
                    val docId = DocumentsContract.getTreeDocumentId(uri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val path = if (split.isNotEmpty()) split[1] else split[0]
                    val repoPath = "${Environment.getExternalStorageDirectory()}/$path"
                    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                    tag(TAG).d { "Selected repository path is $repoPath" }

                    if (Environment.getExternalStorageDirectory().path == repoPath) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.sdcard_root_warning_title))
                            .setMessage(getString(R.string.sdcard_root_warning_message))
                            .setPositiveButton("Remove everything") { _, _ ->
                                prefs.edit { putString("git_external_repo", uri?.path) }
                            }
                            .setNegativeButton(R.string.dialog_cancel, null)
                            .show()
                    }
                    prefs.edit { putString("git_external_repo", repoPath) }
                }
                EXPORT_PASSWORDS -> {
                    val uri = data.data

                    if (uri != null) {
                        val targetDirectory = DocumentFile.fromTreeUri(applicationContext, uri)

                        if (targetDirectory != null) {
                            exportPasswords(targetDirectory)
                        }
                    }
                }
                SET_CUSTOM_XKPWD_DICT -> {
                    val uri: Uri = data.data ?: throw IOException("Unable to open file")

                    Toast.makeText(
                        this,
                        this.resources.getString(R.string.xkpwgen_custom_dict_imported, uri.path),
                        Toast.LENGTH_SHORT
                    ).show()
                    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                    prefs.edit { putString("pref_key_custom_dict", uri.toString()) }

                    val customDictPref = prefsFragment.findPreference<Preference>("pref_key_custom_dict")
                    setCustomDictSummary(customDictPref, uri)
                    // copy user selected file to internal storage
                    val inputStream = this.contentResolver.openInputStream(uri)
                    val customDictFile = File(this.filesDir.toString(), XkpwdDictionary.XKPWD_CUSTOM_DICT_FILE)
                    FileUtils.copyInputStreamToFile(inputStream, customDictFile)

                    setResult(Activity.RESULT_OK)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Exports passwords to the given directory.
     *
     * Recursively copies the existing password store to an external directory.
     *
     * @param targetDirectory directory to copy password directory to.
     */
    private fun exportPasswords(targetDirectory: DocumentFile) {

        val repositoryDirectory = requireNotNull(PasswordRepository.getRepositoryDirectory(applicationContext))
        val sourcePassDir = DocumentFile.fromFile(repositoryDirectory)

        tag(TAG).d { "Copying ${repositoryDirectory.path} to $targetDirectory" }

        val dateString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDateTime
                .now()
                .format(DateTimeFormatter.ISO_DATE_TIME)
        } else {
            String.format("%tFT%<tRZ", Calendar.getInstance(TimeZone.getTimeZone("Z")))
        }

        val passDir = targetDirectory.createDirectory("password_store_$dateString")

        if (passDir != null) {
            copyDirToDir(sourcePassDir, passDir)
        }
    }

    /**
     * Copies a password file to a given directory.
     *
     * Note: this does not preserve last modified time.
     *
     * @param passwordFile password file to copy.
     * @param targetDirectory target directory to copy password.
     */
    private fun copyFileToDir(passwordFile: DocumentFile, targetDirectory: DocumentFile) {
        val sourceInputStream = contentResolver.openInputStream(passwordFile.uri)
        val name = passwordFile.name
        val targetPasswordFile = targetDirectory.createFile("application/octet-stream", name!!)
        if (targetPasswordFile?.exists() == true) {
            val destOutputStream = contentResolver.openOutputStream(targetPasswordFile.uri)

            if (destOutputStream != null && sourceInputStream != null) {
                sourceInputStream.copyTo(destOutputStream, 1024)

                sourceInputStream.close()
                destOutputStream.close()
            }
        }
    }

    /**
     * Recursively copies a directory to a destination.
     *
     *  @param sourceDirectory directory to copy from.
     *  @param sourceDirectory directory to copy to.
     */
    private fun copyDirToDir(sourceDirectory: DocumentFile, targetDirectory: DocumentFile) {
        sourceDirectory.listFiles().forEach { file ->
            if (file.isDirectory) {
                // Create new directory and recurse
                val newDir = targetDirectory.createDirectory(file.name!!)
                copyDirToDir(file, newDir!!)
            } else {
                copyFileToDir(file, targetDirectory)
            }
        }
    }

    companion object {
        private const val IMPORT_SSH_KEY = 1
        private const val IMPORT_PGP_KEY = 2
        private const val EDIT_GIT_INFO = 3
        private const val SELECT_GIT_DIRECTORY = 4
        private const val EXPORT_PASSWORDS = 5
        private const val EDIT_GIT_CONFIG = 6
        private const val SET_CUSTOM_XKPWD_DICT = 7
        private const val TAG = "UserPreference"

        /**
         * Set custom dictionary summary
         */
        @JvmStatic
        private fun setCustomDictSummary(customDictPref: Preference?, uri: Uri) {
            val fileName = uri.path?.substring(uri.path?.lastIndexOf(":")!! + 1)
            customDictPref?.summary = "Selected dictionary: $fileName"
        }
    }
}
