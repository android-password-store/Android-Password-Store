/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.settings

import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.pref
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.data.repo.PasswordRepository
import dev.msfjarvis.aps.ui.git.config.GitConfigActivity
import dev.msfjarvis.aps.ui.git.config.GitServerConfigActivity
import dev.msfjarvis.aps.ui.proxy.ProxySelectorActivity
import dev.msfjarvis.aps.ui.sshkeygen.ShowSshKeyFragment
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.extensions.snackbar
import dev.msfjarvis.aps.util.git.sshj.SshKey
import dev.msfjarvis.aps.util.settings.GitSettings
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RepositorySettings(val activity: FragmentActivity) : SettingsProvider {

    private val encryptedPreferences by lazy(LazyThreadSafetyMode.NONE) { activity.getEncryptedGitPrefs() }

    private fun <T : FragmentActivity> launchActivity(clazz: Class<T>) {
        activity.startActivity(Intent(activity, clazz))
    }

    private val sshKeyImportAction = activity.registerForActivityResult(OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        with(activity) {
            runCatching {
                SshKey.import(uri)
                Toast.makeText(this, resources.getString(R.string.ssh_key_success_dialog_title), Toast.LENGTH_LONG).show()
                setResult(AppCompatActivity.RESULT_OK)
                finish()
            }.onFailure { e ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(resources.getString(R.string.ssh_key_error_dialog_title))
                    .setMessage(e.message)
                    .setPositiveButton(resources.getString(R.string.dialog_ok), null)
                    .show()
            }
        }
    }

    /**
     * Opens a file explorer to import the private key
     */
    private fun importSshKey() {
        if (SshKey.exists) {
            MaterialAlertDialogBuilder(activity).run {
                setTitle(R.string.ssh_keygen_existing_title)
                setMessage(R.string.ssh_keygen_existing_message)
                setPositiveButton(R.string.ssh_keygen_existing_replace) { _, _ ->
                    sshKeyImportAction.launch(arrayOf("*/*"))
                }
                setNegativeButton(R.string.ssh_keygen_existing_keep) { _, _ -> }
                show()
            }
        } else {
            sshKeyImportAction.launch(arrayOf("*/*"))
        }
    }

    override fun provideSettings(builder: PreferenceScreen.Builder) {
        builder.apply {
            pref(PreferenceKeys.GIT_SERVER_INFO) {
                titleRes = R.string.pref_edit_git_server_settings
                visible = PasswordRepository.isGitRepo()
                onClick {
                    launchActivity(GitServerConfigActivity::class.java)
                    true
                }
            }
            pref(PreferenceKeys.PROXY_SETTINGS) {
                titleRes = R.string.pref_edit_proxy_settings
                visible = GitSettings.url?.startsWith("https") == true && PasswordRepository.isGitRepo()
                onClick {
                    launchActivity(ProxySelectorActivity::class.java)
                    true
                }
            }
            pref(PreferenceKeys.GIT_CONFIG) {
                titleRes = R.string.pref_edit_git_config
                visible = PasswordRepository.isGitRepo()
                onClick {
                    launchActivity(GitConfigActivity::class.java)
                    true
                }
            }
            pref(PreferenceKeys.SSH_KEY) {
                titleRes = R.string.pref_import_ssh_key_title
                visible = PasswordRepository.isGitRepo()
                onClick {
                    importSshKey()
                    true
                }
            }
            pref(PreferenceKeys.SSH_SEE_KEY) {
                titleRes = R.string.pref_ssh_see_key_title
                visible = PasswordRepository.isGitRepo()
                onClick {
                    ShowSshKeyFragment().show(activity.supportFragmentManager, "public_key")
                    true
                }
            }
            pref(PreferenceKeys.CLEAR_SAVED_PASS) {
                fun Preference.updatePref() {
                    val sshPass = encryptedPreferences.getString(PreferenceKeys.SSH_KEY_LOCAL_PASSPHRASE)
                    val httpsPass = encryptedPreferences.getString(PreferenceKeys.HTTPS_PASSWORD)
                    if (sshPass == null && httpsPass == null) {
                        visible = false
                        return
                    }
                    when {
                        httpsPass != null -> titleRes = R.string.clear_saved_passphrase_https
                        sshPass != null -> titleRes = R.string.clear_saved_passphrase_ssh
                    }
                    visible = true
                    requestRebind()
                }
                onClick {
                    updatePref()
                    true
                }
                updatePref()
            }
            pref(PreferenceKeys.SSH_OPENKEYSTORE_CLEAR_KEY_ID) {
                titleRes = R.string.pref_title_openkeystore_clear_keyid
                visible = activity.sharedPrefs.getString(PreferenceKeys.SSH_OPENKEYSTORE_KEYID)?.isNotEmpty()
                    ?: false
                onClick {
                    activity.sharedPrefs.edit { putString(PreferenceKeys.SSH_OPENKEYSTORE_KEYID, null) }
                    visible = false
                    true
                }
            }
            pref(PreferenceKeys.GIT_DELETE_REPO) {
                titleRes = R.string.pref_git_delete_repo_title
                summaryRes = R.string.pref_git_delete_repo_summary
                visible = !activity.sharedPrefs.getBoolean(PreferenceKeys.GIT_EXTERNAL, false)
                onClick {
                    val repoDir = PasswordRepository.getRepositoryDirectory()
                    MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.pref_dialog_delete_title)
                        .setMessage(activity.getString(R.string.dialog_delete_msg, repoDir))
                        .setCancelable(false)
                        .setPositiveButton(R.string.dialog_delete) { dialogInterface, _ ->
                            runCatching {
                                PasswordRepository.getRepositoryDirectory().deleteRecursively()
                                PasswordRepository.closeRepository()
                            }.onFailure {
                                it.message?.let { message ->
                                    activity.snackbar(message = message)
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                                activity.getSystemService<ShortcutManager>()?.apply {
                                    removeDynamicShortcuts(dynamicShortcuts.map { it.id }.toMutableList())
                                }
                            }
                            activity.sharedPrefs.edit { putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, false) }
                            dialogInterface.cancel()
                            activity.finish()
                        }
                        .setNegativeButton(R.string.dialog_do_not_delete) { dialogInterface, _ -> run { dialogInterface.cancel() } }
                        .show()
                    true
                }
            }
        }
    }
}
