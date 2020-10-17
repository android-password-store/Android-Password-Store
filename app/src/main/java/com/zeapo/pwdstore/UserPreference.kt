/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.TextUtils
import android.view.MenuItem
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.github.ajalt.timberkt.Timber.tag
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.w
import com.github.androidpasswordstore.autofillparser.BrowserAutofillSupportLevel
import com.github.androidpasswordstore.autofillparser.getInstalledBrowsersWithAutofillSupportLevel
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.autofill.AutofillPreferenceActivity
import com.zeapo.pwdstore.crypto.BasePgpActivity
import com.zeapo.pwdstore.git.GitConfigActivity
import com.zeapo.pwdstore.git.GitServerConfigActivity
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.sshj.SshKey
import com.zeapo.pwdstore.pwgenxkpwd.XkpwdDictionary
import com.zeapo.pwdstore.sshkeygen.ShowSshKeyFragment
import com.zeapo.pwdstore.sshkeygen.SshKeyGenActivity
import com.zeapo.pwdstore.ui.proxy.ProxySelectorActivity
import com.zeapo.pwdstore.utils.BiometricAuthenticator
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.autofillManager
import com.zeapo.pwdstore.utils.getEncryptedGitPrefs
import com.zeapo.pwdstore.utils.getString
import com.zeapo.pwdstore.utils.sharedPrefs
import java.io.File

typealias ClickListener = Preference.OnPreferenceClickListener
typealias ChangeListener = Preference.OnPreferenceChangeListener

class UserPreference : AppCompatActivity() {

    private lateinit var prefsFragment: PrefsFragment
    private var fromIntent = false

    @Suppress("DEPRECATION")
    private val directorySelectAction = registerForActivityResult(OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult

        tag(TAG).d { "Selected repository URI is $uri" }
        // TODO: This is fragile. Workaround until PasswordItem is backed by DocumentFile
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val path = if (split.size > 1) split[1] else split[0]
        val repoPath = "${Environment.getExternalStorageDirectory()}/$path"
        val prefs = sharedPrefs

        tag(TAG).d { "Selected repository path is $repoPath" }

        if (Environment.getExternalStorageDirectory().path == repoPath) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.sdcard_root_warning_title))
                .setMessage(getString(R.string.sdcard_root_warning_message))
                .setPositiveButton("Remove everything") { _, _ ->
                    prefs.edit { putString(PreferenceKeys.GIT_EXTERNAL_REPO, uri.path) }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
        prefs.edit { putString(PreferenceKeys.GIT_EXTERNAL_REPO, repoPath) }
        if (fromIntent) {
            setResult(RESULT_OK)
            finish()
        }

    }

    private val sshKeyImportAction = registerForActivityResult(OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            SshKey.import(uri)

            Toast.makeText(this, resources.getString(R.string.ssh_key_success_dialog_title), Toast.LENGTH_LONG).show()
            setResult(RESULT_OK)
            finish()
        }.onFailure { e ->
            MaterialAlertDialogBuilder(this)
                .setTitle(resources.getString(R.string.ssh_key_error_dialog_title))
                .setMessage(e.message)
                .setPositiveButton(resources.getString(R.string.dialog_ok), null)
                .show()
        }
    }

    private val storeExportAction = registerForActivityResult(object : OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return super.createIntent(context, input).apply {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            }
        }
    }) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val targetDirectory = DocumentFile.fromTreeUri(applicationContext, uri)

        if (targetDirectory != null) {
            val service = Intent(applicationContext, PasswordExportService::class.java).apply {
                action = PasswordExportService.ACTION_EXPORT_PASSWORD
                putExtra("uri", uri)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(service)
            } else {
                startService(service)
            }
        }
    }

    private val storeCustomXkpwdDictionaryAction = registerForActivityResult(OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult

        Toast.makeText(
            this,
            this.resources.getString(R.string.xkpwgen_custom_dict_imported, uri.path),
            Toast.LENGTH_SHORT
        ).show()

        sharedPrefs.edit { putString(PreferenceKeys.PREF_KEY_CUSTOM_DICT, uri.toString()) }

        val customDictPref = prefsFragment.findPreference<Preference>(PreferenceKeys.PREF_KEY_CUSTOM_DICT)
        setCustomDictSummary(customDictPref, uri)
        // copy user selected file to internal storage
        val inputStream = contentResolver.openInputStream(uri)
        val customDictFile = File(filesDir.toString(), XkpwdDictionary.XKPWD_CUSTOM_DICT_FILE).outputStream()
        inputStream?.copyTo(customDictFile, 1024)
        inputStream?.close()
        customDictFile.close()

        setResult(RESULT_OK)
    }

    class PrefsFragment : PreferenceFragmentCompat() {

        private var autoFillEnablePreference: SwitchPreferenceCompat? = null
        private var clearSavedPassPreference: Preference? = null
        private var viewSshKeyPreference: Preference? = null
        private lateinit var autofillDependencies: List<Preference>
        private lateinit var oreoAutofillDependencies: List<Preference>
        private lateinit var prefsActivity: UserPreference
        private lateinit var sharedPreferences: SharedPreferences
        private lateinit var encryptedPreferences: SharedPreferences

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            prefsActivity = requireActivity() as UserPreference
            val context = requireContext()
            sharedPreferences = preferenceManager.sharedPreferences
            encryptedPreferences = requireActivity().getEncryptedGitPrefs()

            addPreferencesFromResource(R.xml.preference)

            // Git preferences
            val gitServerPreference = findPreference<Preference>(PreferenceKeys.GIT_SERVER_INFO)
            val openkeystoreIdPreference = findPreference<Preference>(PreferenceKeys.SSH_OPENKEYSTORE_CLEAR_KEY_ID)
            val gitConfigPreference = findPreference<Preference>(PreferenceKeys.GIT_CONFIG)
            val sshKeyPreference = findPreference<Preference>(PreferenceKeys.SSH_KEY)
            val sshKeygenPreference = findPreference<Preference>(PreferenceKeys.SSH_KEYGEN)
            viewSshKeyPreference = findPreference(PreferenceKeys.SSH_SEE_KEY)
            clearSavedPassPreference = findPreference(PreferenceKeys.CLEAR_SAVED_PASS)
            val deleteRepoPreference = findPreference<Preference>(PreferenceKeys.GIT_DELETE_REPO)
            val externalGitRepositoryPreference = findPreference<Preference>(PreferenceKeys.GIT_EXTERNAL)
            val selectExternalGitRepositoryPreference = findPreference<Preference>(PreferenceKeys.PREF_SELECT_EXTERNAL)

            if (!PasswordRepository.isGitRepo()) {
                listOfNotNull(
                    gitServerPreference,
                    gitConfigPreference,
                    sshKeyPreference,
                    viewSshKeyPreference,
                    clearSavedPassPreference,
                ).forEach {
                    it.parent?.removePreference(it)
                }
            }

            // General preferences
            val showTimePreference = findPreference<Preference>(PreferenceKeys.GENERAL_SHOW_TIME)
            val clearClipboard20xPreference = findPreference<CheckBoxPreference>(PreferenceKeys.CLEAR_CLIPBOARD_20X)

            // Autofill preferences
            autoFillEnablePreference = findPreference(PreferenceKeys.AUTOFILL_ENABLE)
            val oreoAutofillDirectoryStructurePreference = findPreference<ListPreference>(PreferenceKeys.OREO_AUTOFILL_DIRECTORY_STRUCTURE)
            val oreoAutofillDefaultUsername = findPreference<EditTextPreference>(PreferenceKeys.OREO_AUTOFILL_DEFAULT_USERNAME)
            val oreoAutofillCustomPublixSuffixes = findPreference<EditTextPreference>(PreferenceKeys.OREO_AUTOFILL_CUSTOM_PUBLIC_SUFFIXES)
            val autoFillAppsPreference = findPreference<Preference>(PreferenceKeys.AUTOFILL_APPS)
            val autoFillDefaultPreference = findPreference<CheckBoxPreference>(PreferenceKeys.AUTOFILL_DEFAULT)
            val autoFillAlwaysShowDialogPreference = findPreference<CheckBoxPreference>(PreferenceKeys.AUTOFILL_ALWAYS)
            val autoFillShowFullNamePreference = findPreference<CheckBoxPreference>(PreferenceKeys.AUTOFILL_FULL_PATH)
            autofillDependencies = listOfNotNull(
                autoFillAppsPreference,
                autoFillDefaultPreference,
                autoFillAlwaysShowDialogPreference,
                autoFillShowFullNamePreference,
            )
            oreoAutofillDependencies = listOfNotNull(
                oreoAutofillDirectoryStructurePreference,
                oreoAutofillDefaultUsername,
                oreoAutofillCustomPublixSuffixes,
            )
            oreoAutofillCustomPublixSuffixes?.apply {
                setOnBindEditTextListener {
                    it.isSingleLine = false
                    it.setHint(R.string.preference_custom_public_suffixes_hint)
                }
            }

            // Misc preferences
            val appVersionPreference = findPreference<Preference>(PreferenceKeys.APP_VERSION)

            selectExternalGitRepositoryPreference?.summary = sharedPreferences.getString(PreferenceKeys.GIT_EXTERNAL_REPO)
                ?: getString(R.string.no_repo_selected)
            deleteRepoPreference?.isVisible = !sharedPreferences.getBoolean(PreferenceKeys.GIT_EXTERNAL, false)
            clearClipboard20xPreference?.isVisible = sharedPreferences.getString(PreferenceKeys.GENERAL_SHOW_TIME)?.toInt() != 0
            openkeystoreIdPreference?.isVisible = sharedPreferences.getString(PreferenceKeys.SSH_OPENKEYSTORE_KEYID)?.isNotEmpty()
                ?: false

            updateAutofillSettings()
            updateClearSavedPassphrasePrefs()

            appVersionPreference?.summary = "Version: ${BuildConfig.VERSION_NAME}"

            sshKeyPreference?.onPreferenceClickListener = ClickListener {
                prefsActivity.getSshKey()
                true
            }

            sshKeygenPreference?.onPreferenceClickListener = ClickListener {
                prefsActivity.makeSshKey(true)
                true
            }

            viewSshKeyPreference?.onPreferenceClickListener = ClickListener {
                val df = ShowSshKeyFragment()
                df.show(parentFragmentManager, "public_key")
                true
            }

            clearSavedPassPreference?.onPreferenceClickListener = ClickListener {
                encryptedPreferences.edit {
                    if (encryptedPreferences.getString(PreferenceKeys.HTTPS_PASSWORD) != null)
                        remove(PreferenceKeys.HTTPS_PASSWORD)
                    else if (encryptedPreferences.getString(PreferenceKeys.SSH_KEY_LOCAL_PASSPHRASE) != null)
                        remove(PreferenceKeys.SSH_KEY_LOCAL_PASSPHRASE)
                }
                updateClearSavedPassphrasePrefs()
                true
            }

            openkeystoreIdPreference?.onPreferenceClickListener = ClickListener {
                sharedPreferences.edit { putString(PreferenceKeys.SSH_OPENKEYSTORE_KEYID, null) }
                it.isVisible = false
                true
            }

            gitServerPreference?.onPreferenceClickListener = ClickListener {
                startActivity(Intent(prefsActivity, GitServerConfigActivity::class.java))
                true
            }

            gitConfigPreference?.onPreferenceClickListener = ClickListener {
                startActivity(Intent(prefsActivity, GitConfigActivity::class.java))
                true
            }

            deleteRepoPreference?.onPreferenceClickListener = ClickListener {
                val repoDir = PasswordRepository.getRepositoryDirectory()
                MaterialAlertDialogBuilder(prefsActivity)
                    .setTitle(R.string.pref_dialog_delete_title)
                    .setMessage(resources.getString(R.string.dialog_delete_msg, repoDir))
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_delete) { dialogInterface, _ ->
                        runCatching {
                            PasswordRepository.getRepositoryDirectory().deleteRecursively()
                            PasswordRepository.closeRepository()
                        }.onFailure {
                            // TODO Handle the different cases of exceptions
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            requireContext().getSystemService<ShortcutManager>()?.apply {
                                removeDynamicShortcuts(dynamicShortcuts.map { it.id }.toMutableList())
                            }
                        }
                        sharedPreferences.edit { putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, false) }
                        dialogInterface.cancel()
                        prefsActivity.finish()
                    }
                    .setNegativeButton(R.string.dialog_do_not_delete) { dialogInterface, _ -> run { dialogInterface.cancel() } }
                    .show()

                true
            }

            selectExternalGitRepositoryPreference?.summary =
                sharedPreferences.getString(PreferenceKeys.GIT_EXTERNAL_REPO)
                    ?: context.getString(R.string.no_repo_selected)
            selectExternalGitRepositoryPreference?.onPreferenceClickListener = ClickListener {
                prefsActivity.selectExternalGitRepository()
                true
            }

            val resetRepo = Preference.OnPreferenceChangeListener { _, o ->
                deleteRepoPreference?.isVisible = !(o as Boolean)
                PasswordRepository.closeRepository()
                sharedPreferences.edit { putBoolean(PreferenceKeys.REPO_CHANGED, true) }
                true
            }

            selectExternalGitRepositoryPreference?.onPreferenceChangeListener = resetRepo
            externalGitRepositoryPreference?.onPreferenceChangeListener = resetRepo

            autoFillAppsPreference?.onPreferenceClickListener = ClickListener {
                val intent = Intent(prefsActivity, AutofillPreferenceActivity::class.java)
                startActivity(intent)
                true
            }

            autoFillEnablePreference?.onPreferenceClickListener = ClickListener {
                onEnableAutofillClick()
                true
            }

            findPreference<Preference>(PreferenceKeys.EXPORT_PASSWORDS)?.apply {
                isVisible = sharedPreferences.getBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, false)
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    prefsActivity.exportPasswords()
                    true
                }
            }

            showTimePreference?.onPreferenceChangeListener = ChangeListener { _, newValue: Any? ->
                runCatching {
                    val isEnabled = newValue.toString().toInt() != 0
                    clearClipboard20xPreference?.isVisible = isEnabled
                    true
                }.getOr(false)
            }

            showTimePreference?.summaryProvider = Preference.SummaryProvider<Preference> {
                getString(R.string.pref_clipboard_timeout_summary, sharedPreferences.getString
                (PreferenceKeys.GENERAL_SHOW_TIME, "45"))
            }

            findPreference<CheckBoxPreference>(PreferenceKeys.ENABLE_DEBUG_LOGGING)?.isVisible = !BuildConfig.ENABLE_DEBUG_FEATURES

            findPreference<CheckBoxPreference>(PreferenceKeys.BIOMETRIC_AUTH)?.apply {
                val canAuthenticate = BiometricAuthenticator.canAuthenticate(prefsActivity)

                if (!canAuthenticate) {
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
                                        putBoolean(PreferenceKeys.BIOMETRIC_AUTH, checked)
                                        isEnabled = true
                                    }
                                    else -> {
                                        // If any error occurs, revert back to the previous state. This
                                        // catch-all clause includes the cancellation case.
                                        putBoolean(PreferenceKeys.BIOMETRIC_AUTH, !checked)
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

            findPreference<Preference>(PreferenceKeys.PROXY_SETTINGS)?.onPreferenceClickListener = ClickListener {
                startActivity(Intent(requireContext(), ProxySelectorActivity::class.java))
                true
            }

            findPreference<CheckBoxPreference>(PreferenceKeys.SYNC_ON_LAUNCH)?.isVisible = !GitSettings.url.isNullOrEmpty()

            val prefCustomXkpwdDictionary = findPreference<Preference>(PreferenceKeys.PREF_KEY_CUSTOM_DICT)
            prefCustomXkpwdDictionary?.onPreferenceClickListener = ClickListener {
                prefsActivity.storeCustomDictionaryPath()
                true
            }
            val dictUri = sharedPreferences.getString(PreferenceKeys.PREF_KEY_CUSTOM_DICT) ?: ""

            if (!TextUtils.isEmpty(dictUri)) {
                setCustomDictSummary(prefCustomXkpwdDictionary, Uri.parse(dictUri))
            }

            val prefIsCustomDict = findPreference<CheckBoxPreference>(PreferenceKeys.PREF_KEY_IS_CUSTOM_DICT)
            val prefCustomDictPicker = findPreference<Preference>(PreferenceKeys.PREF_KEY_CUSTOM_DICT)
            val prefPwgenType = findPreference<ListPreference>(PreferenceKeys.PREF_KEY_PWGEN_TYPE)
            updateXkPasswdPrefsVisibility(prefPwgenType?.value, prefIsCustomDict, prefCustomDictPicker)

            prefPwgenType?.onPreferenceChangeListener = ChangeListener { _, newValue ->
                updateXkPasswdPrefsVisibility(newValue, prefIsCustomDict, prefCustomDictPicker)
                true
            }

            prefIsCustomDict?.onPreferenceChangeListener = ChangeListener { _, newValue ->
                if (!(newValue as Boolean)) {
                    val customDictFile = File(context.filesDir, XkpwdDictionary.XKPWD_CUSTOM_DICT_FILE)
                    if (customDictFile.exists() && !customDictFile.delete()) {
                        w { "Failed to delete custom XkPassword dictionary: $customDictFile" }
                    }
                    prefCustomDictPicker?.setSummary(R.string.xkpwgen_pref_custom_dict_picker_summary)
                }
                true
            }
        }

        private fun updateXkPasswdPrefsVisibility(newValue: Any?, prefIsCustomDict: CheckBoxPreference?, prefCustomDictPicker: Preference?) {
            when (newValue as String) {
                BasePgpActivity.KEY_PWGEN_TYPE_CLASSIC -> {
                    prefIsCustomDict?.isVisible = false
                    prefCustomDictPicker?.isVisible = false
                }
                BasePgpActivity.KEY_PWGEN_TYPE_XKPASSWD -> {
                    prefIsCustomDict?.isVisible = true
                    prefCustomDictPicker?.isVisible = true
                }
            }
        }

        private fun updateAutofillSettings() {
            val isAccessibilityServiceEnabled = prefsActivity.isAccessibilityServiceEnabled
            val isAutofillServiceEnabled = prefsActivity.isAutofillServiceEnabled
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
                val sshPass = encryptedPreferences.getString(PreferenceKeys.SSH_KEY_LOCAL_PASSPHRASE)
                val httpsPass = encryptedPreferences.getString(PreferenceKeys.HTTPS_PASSWORD)
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

        private fun updateViewSshPubkeyPref() {
            viewSshKeyPreference?.isVisible = SshKey.canShowSshPublicKey
        }

        private fun onEnableAutofillClick() {
            if (prefsActivity.isAccessibilityServiceEnabled) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else if (prefsActivity.isAutofillServiceEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    prefsActivity.autofillManager!!.disableAutofillServices()
                else
                    throw IllegalStateException("isAutofillServiceEnabled == true, but Build.VERSION.SDK_INT < Build.VERSION_CODES.O")
            } else {
                val enableOreoAutofill = prefsActivity.isAutofillServiceSupported
                MaterialAlertDialogBuilder(prefsActivity).run {
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
            updateViewSshPubkeyPref()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setResult(RESULT_OK)
        finish()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.getStringExtra("operation")) {
            "get_ssh_key" -> getSshKey()
            "make_ssh_key" -> makeSshKey(false)
            "git_external" -> {
                fromIntent = true
                selectExternalGitRepository()
            }
        }
        prefsFragment = PrefsFragment()

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, prefsFragment)
            .commit()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    @Suppress("Deprecation") // for Environment.getExternalStorageDirectory()
    fun selectExternalGitRepository() {
        MaterialAlertDialogBuilder(this)
            .setTitle(this.resources.getString(R.string.external_repository_dialog_title))
            .setMessage(this.resources.getString(R.string.external_repository_dialog_text))
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                directorySelectAction.launch(null)
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
                setResult(RESULT_OK)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun importSshKey() {
        sshKeyImportAction.launch(arrayOf("*/*"))
    }

    /**
     * Opens a file explorer to import the private key
     */
    private fun getSshKey() {
        if (SshKey.exists) {
            MaterialAlertDialogBuilder(this).run {
                setTitle(R.string.ssh_keygen_existing_title)
                setMessage(R.string.ssh_keygen_existing_message)
                setPositiveButton(R.string.ssh_keygen_existing_replace) { _, _ ->
                    importSshKey()
                }
                setNegativeButton(R.string.ssh_keygen_existing_keep) { _, _ -> }
                show()
            }
        } else {
            importSshKey()
        }
    }

    /**
     * Exports the passwords
     */
    private fun exportPasswords() {
        storeExportAction.launch(null)
    }

    /**
     * Opens a key generator to generate a public/private key pair
     */
    fun makeSshKey(fromPreferences: Boolean) {
        val intent = Intent(applicationContext, SshKeyGenActivity::class.java)
        startActivity(intent)
        if (!fromPreferences) {
            setResult(RESULT_OK)
            finish()
        }
    }

    /**
     * Pick custom xkpwd dictionary from sdcard
     */
    private fun storeCustomDictionaryPath() {
        storeCustomXkpwdDictionaryAction.launch(arrayOf("*/*"))
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

    companion object {

        private const val TAG = "UserPreference"

        fun createDirectorySelectionIntent(context: Context): Intent {
            return Intent(context, UserPreference::class.java).run {
                putExtra("operation", "git_external")
            }
        }

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
