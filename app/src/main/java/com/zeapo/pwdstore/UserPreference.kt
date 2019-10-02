/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0
 */
package com.zeapo.pwdstore

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.content.getSystemService
import androidx.documentfile.provider.DocumentFile
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.autofill.AutofillPreferenceActivity
import com.zeapo.pwdstore.crypto.PgpActivity
import com.zeapo.pwdstore.git.GitActivity
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.auth.AuthenticationResult
import com.zeapo.pwdstore.utils.auth.Authenticator
import org.apache.commons.io.FileUtils
import org.openintents.openpgp.util.OpenPgpUtils
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
        private var autofillDependencies = listOf<Preference?>()
        private var autoFillEnablePreference: CheckBoxPreference? = null
        private lateinit var callingActivity: UserPreference

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            callingActivity = requireActivity() as UserPreference
            val context = requireContext()
            val sharedPreferences = preferenceManager.sharedPreferences

            addPreferencesFromResource(R.xml.preference)

            // Git preferences
            val gitServerPreference = findPreference<Preference>("git_server_info")
            val gitConfigPreference = findPreference<Preference>("git_config")
            val sshKeyPreference = findPreference<Preference>("ssh_key")
            val sshKeygenPreference = findPreference<Preference>("ssh_keygen")
            val sshClearPassphrasePreference = findPreference<Preference>("ssh_key_clear_passphrase")
            val clearHotpIncrementPreference = findPreference<Preference>("hotp_remember_clear_choice")
            val viewSshKeyPreference = findPreference<Preference>("ssh_see_key")
            val deleteRepoPreference = findPreference<Preference>("git_delete_repo")
            val externalGitRepositoryPreference = findPreference<Preference>("git_external")
            val selectExternalGitRepositoryPreference = findPreference<Preference>("pref_select_external")

            // Crypto preferences
            val keyPreference = findPreference<Preference>("openpgp_key_id_pref")

            // General preferences
            val clearAfterCopyPreference = findPreference<CheckBoxPreference>("clear_after_copy")
            val clearClipboard20xPreference = findPreference<CheckBoxPreference>("clear_clipboard_20x")

            // Autofill preferences
            autoFillEnablePreference = findPreference<CheckBoxPreference>("autofill_enable")
            val autoFillAppsPreference = findPreference<Preference>("autofill_apps")
            val autoFillDefaultPreference = findPreference<CheckBoxPreference>("autofill_default")
            val autoFillAlwaysShowDialogPreference = findPreference<CheckBoxPreference>("autofill_always")
            autofillDependencies = listOf(
                    autoFillAppsPreference,
                    autoFillDefaultPreference,
                    autoFillAlwaysShowDialogPreference
            )

            // Misc preferences
            val appVersionPreference = findPreference<Preference>("app_version")

            selectExternalGitRepositoryPreference?.summary = sharedPreferences.getString("git_external_repo", getString(R.string.no_repo_selected))
            viewSshKeyPreference?.isVisible = sharedPreferences.getBoolean("use_generated_key", false)
            deleteRepoPreference?.isVisible = !sharedPreferences.getBoolean("git_external", false)
            sshClearPassphrasePreference?.isVisible = sharedPreferences.getString("ssh_key_passphrase", null)?.isNotEmpty()
                    ?: false
            clearHotpIncrementPreference?.isVisible = sharedPreferences.getBoolean("hotp_remember_check", false)
            clearAfterCopyPreference?.isVisible = sharedPreferences.getString("general_show_time", "45")?.toInt() != 0
            clearClipboard20xPreference?.isVisible = sharedPreferences.getString("general_show_time", "45")?.toInt() != 0
            val selectedKeys = (sharedPreferences.getStringSet("openpgp_key_ids_set", null)
                    ?: HashSet<String>()).toTypedArray()
            keyPreference?.summary = if (selectedKeys.isEmpty()) {
                this.resources.getString(R.string.pref_no_key_selected)
            } else {
                selectedKeys.joinToString(separator = ";") { s ->
                    OpenPgpUtils.convertKeyIdToHex(java.lang.Long.valueOf(s))
                }
            }

            // see if the autofill service is enabled and check the preference accordingly
            autoFillEnablePreference?.isChecked = callingActivity.isServiceEnabled
            autofillDependencies.forEach { it?.isVisible = callingActivity.isServiceEnabled }

            appVersionPreference?.summary = "Version: ${BuildConfig.VERSION_NAME}"

            keyPreference?.onPreferenceClickListener = ClickListener {
                val intent = Intent(callingActivity, PgpActivity::class.java)
                intent.putExtra("OPERATION", "GET_KEY_ID")
                startActivityForResult(intent, IMPORT_PGP_KEY)
                true
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
                val df = SshKeyGen.ShowSshKeyFragment()
                df.show(requireFragmentManager(), "public_key")
                true
            }

            sshClearPassphrasePreference?.onPreferenceClickListener = ClickListener {
                sharedPreferences.edit().putString("ssh_key_passphrase", null).apply()
                it.isVisible = false
                true
            }

            clearHotpIncrementPreference?.onPreferenceClickListener = ClickListener {
                sharedPreferences.edit().putBoolean("hotp_remember_check", false).apply()
                it.isVisible = false
                true
            }

            gitServerPreference?.onPreferenceClickListener = ClickListener {
                val intent = Intent(callingActivity, GitActivity::class.java)
                intent.putExtra("Operation", GitActivity.EDIT_SERVER)
                startActivityForResult(intent, EDIT_GIT_INFO)
                true
            }

            gitConfigPreference?.onPreferenceClickListener = ClickListener {
                val intent = Intent(callingActivity, GitActivity::class.java)
                intent.putExtra("Operation", GitActivity.EDIT_GIT_CONFIG)
                startActivityForResult(intent, EDIT_GIT_CONFIG)
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

                            sharedPreferences.edit().putBoolean("repository_initialized", false).apply()
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
                sharedPreferences.edit().putBoolean("repo_changed", true).apply()
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
                var isEnabled = callingActivity.isServiceEnabled
                if (isEnabled) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    MaterialAlertDialogBuilder(callingActivity)
                            .setTitle(R.string.pref_autofill_enable_title)
                            .setView(R.layout.autofill_instructions)
                            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                            .setNegativeButton(R.string.dialog_cancel, null)
                            .setOnDismissListener {
                                isEnabled = callingActivity.isServiceEnabled
                                autoFillEnablePreference?.isChecked = isEnabled
                                autofillDependencies.forEach { it?.isVisible = isEnabled }
                            }
                            .show()
                }
                true
            }

            findPreference<Preference>("export_passwords")?.apply {
                isVisible = sharedPreferences.getBoolean("repository_initialized", false)
                onPreferenceClickListener = Preference.OnPreferenceClickListener {
                    callingActivity.exportPasswords()
                    true
                }
            }

            findPreference<Preference>("general_show_time")?.onPreferenceChangeListener =
                    ChangeListener { _, newValue: Any? ->
                        try {
                            val isEnabled = newValue.toString().toInt() != 0
                            clearAfterCopyPreference?.isVisible = isEnabled
                            clearClipboard20xPreference?.isVisible = isEnabled
                            true
                        } catch (e: NumberFormatException) {
                            false
                        }
                    }

            findPreference<SwitchPreference>("biometric_auth")?.apply {
                val isFingerprintSupported = BiometricManager.from(requireContext()).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
                if (!isFingerprintSupported) {
                    isEnabled = false
                    isChecked = false
                    summary = getString(R.string.biometric_auth_summary_error)
                } else {
                    setOnPreferenceClickListener {
                        val editor = sharedPreferences.edit()
                        val checked = isChecked
                        Authenticator(requireActivity()) { result ->
                            when (result) {
                                is AuthenticationResult.Success -> {
                                    // Apply the changes
                                    editor.putBoolean("biometric_auth", checked)
                                }
                                else -> {
                                    // If any error occurs, revert back to the previous state. This
                                    // catch-all clause includes the cancellation case.
                                    editor.putBoolean("biometric_auth", !checked)
                                    isChecked = !checked
                                }
                            }
                        }.authenticate()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            requireContext().getSystemService<ShortcutManager>()?.apply {
                                removeDynamicShortcuts(dynamicShortcuts.map { it.id }.toMutableList())
                            }
                        }
                        editor.apply()
                        true
                    }
                }
            }
        }

        override fun onResume() {
            super.onResume()
            val isEnabled = callingActivity.isServiceEnabled
            autoFillEnablePreference?.isChecked = isEnabled
            autofillDependencies.forEach { it?.isVisible = isEnabled }
        }
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
        val intent = Intent(applicationContext, SshKeyGen::class.java)
        startActivity(intent)
        if (!fromPreferences) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    @Throws(IOException::class)
    private fun copySshKey(uri: Uri) {
        // TODO: Check if valid SSH Key before import
        val sshKeyInputStream = contentResolver.openInputStream(uri)
        if (sshKeyInputStream != null) {

            val internalKeyFile = File("""$filesDir/.ssh_key""")

            if (internalKeyFile.exists()) {
                internalKeyFile.delete()
                internalKeyFile.createNewFile()
            }

            val sshKeyOutputSteam = internalKeyFile.outputStream()

            sshKeyInputStream.copyTo(sshKeyOutputSteam, 1024)

            sshKeyInputStream.close()
            sshKeyOutputSteam.close()
        } else {
            Toast.makeText(this, getString(R.string.ssh_key_does_not_exist), Toast.LENGTH_LONG).show()
        }
    }

    // Returns whether the autofill service is enabled
    private val isServiceEnabled: Boolean
        get() {
            val am = this
                    .getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val runningServices = am
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            return runningServices
                    .map { it.id.substringBefore("/") }
                    .any { it == BuildConfig.APPLICATION_ID }
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

                        prefs.edit().putBoolean("use_generated_key", false).apply()

                        // Delete the public key from generation
                        File("""$filesDir/.ssh_key.pub""").delete()
                        setResult(Activity.RESULT_OK)

                        finish()
                    } catch (e: IOException) {
                        MaterialAlertDialogBuilder(this)
                                .setTitle(this.resources.getString(R.string.ssh_key_error_dialog_title))
                                .setMessage(this.resources.getString(R.string.ssh_key_error_dialog_text) + e.message)
                                .setPositiveButton(this.resources.getString(R.string.dialog_ok), null)
                                .show()
                    }
                }
                EDIT_GIT_INFO -> {
                }
                SELECT_GIT_DIRECTORY -> {
                    val uri = data.data

                    Log.d(TAG, "Selected repository URI is $uri")
                    // TODO: This is fragile. Workaround until PasswordItem is backed by DocumentFile
                    val docId = DocumentsContract.getTreeDocumentId(uri)
                    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val repoPath = "${Environment.getExternalStorageDirectory()}/${split[1]}"

                    Log.d(TAG, "Selected repository path is $repoPath")

                    if (Environment.getExternalStorageDirectory().path == repoPath) {
                        MaterialAlertDialogBuilder(this)
                                .setTitle(getString(R.string.sdcard_root_warning_title))
                                .setMessage(getString(R.string.sdcard_root_warning_message))
                                .setPositiveButton("Remove everything") { _, _ ->
                                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                                            .edit()
                                            .putString("git_external_repo", uri?.path)
                                            .apply()
                                }
                                .setNegativeButton(R.string.dialog_cancel, null)
                                .show()
                    }

                    PreferenceManager.getDefaultSharedPreferences(applicationContext)
                            .edit()
                            .putString("git_external_repo", repoPath)
                            .apply()
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

        Log.d(TAG, "Copying ${repositoryDirectory.path} to $targetDirectory")

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
        private const val TAG = "UserPreference"
    }
}
