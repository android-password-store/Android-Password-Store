/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.Maxr1998.modernpreferences.Preference
import de.Maxr1998.modernpreferences.PreferenceScreen
import de.Maxr1998.modernpreferences.helpers.checkBox
import de.Maxr1998.modernpreferences.helpers.onClick
import de.Maxr1998.modernpreferences.helpers.pref
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.data.repo.PasswordRepository
import dev.msfjarvis.aps.injection.prefs.GitPreferences
import dev.msfjarvis.aps.ui.git.config.GitConfigActivity
import dev.msfjarvis.aps.ui.git.config.GitServerConfigActivity
import dev.msfjarvis.aps.ui.proxy.ProxySelectorActivity
import dev.msfjarvis.aps.ui.sshkeygen.ShowSshKeyFragment
import dev.msfjarvis.aps.ui.sshkeygen.SshKeyGenActivity
import dev.msfjarvis.aps.ui.sshkeygen.SshKeyImportActivity
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.launchActivity
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.extensions.snackbar
import dev.msfjarvis.aps.util.extensions.unsafeLazy
import dev.msfjarvis.aps.util.git.sshj.SshKey
import dev.msfjarvis.aps.util.settings.GitSettings
import dev.msfjarvis.aps.util.settings.PreferenceKeys

class RepositorySettings(private val activity: FragmentActivity) : SettingsProvider {

  private val generateSshKey =
    activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      showSshKeyPref?.visible = SshKey.canShowSshPublicKey
    }

  private val hiltEntryPoint by unsafeLazy {
    EntryPointAccessors.fromApplication(
      activity.applicationContext,
      RepositorySettingsEntryPoint::class.java,
    )
  }

  private var showSshKeyPref: Preference? = null

  override fun provideSettings(builder: PreferenceScreen.Builder) {
    val encryptedPreferences = hiltEntryPoint.encryptedPreferences()
    val gitSettings = hiltEntryPoint.gitSettings()

    builder.apply {
      checkBox(PreferenceKeys.REBASE_ON_PULL) {
        titleRes = R.string.pref_rebase_on_pull_title
        summaryRes = R.string.pref_rebase_on_pull_summary
        summaryOnRes = R.string.pref_rebase_on_pull_summary_on
        defaultValue = true
      }
      pref(PreferenceKeys.GIT_SERVER_INFO) {
        titleRes = R.string.pref_edit_git_server_settings
        visible = PasswordRepository.isGitRepo()
        onClick {
          activity.launchActivity(GitServerConfigActivity::class.java)
          true
        }
      }
      pref(PreferenceKeys.PROXY_SETTINGS) {
        titleRes = R.string.pref_edit_proxy_settings
        visible = gitSettings.url?.startsWith("https") == true && PasswordRepository.isGitRepo()
        onClick {
          activity.launchActivity(ProxySelectorActivity::class.java)
          true
        }
      }
      pref(PreferenceKeys.GIT_CONFIG) {
        titleRes = R.string.pref_edit_git_config
        visible = PasswordRepository.isGitRepo()
        onClick {
          activity.launchActivity(GitConfigActivity::class.java)
          true
        }
      }
      pref(PreferenceKeys.SSH_KEY) {
        titleRes = R.string.pref_import_ssh_key_title
        visible = PasswordRepository.isGitRepo()
        onClick {
          activity.launchActivity(SshKeyImportActivity::class.java)
          true
        }
      }
      pref(PreferenceKeys.SSH_KEYGEN) {
        titleRes = R.string.pref_ssh_keygen_title
        onClick {
          generateSshKey.launch(Intent(activity, SshKeyGenActivity::class.java))
          true
        }
      }
      showSshKeyPref =
        pref(PreferenceKeys.SSH_SEE_KEY) {
          titleRes = R.string.pref_ssh_see_key_title
          visible = PasswordRepository.isGitRepo() && SshKey.canShowSshPublicKey
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
        visible =
          activity.sharedPrefs.getString(PreferenceKeys.SSH_OPENKEYSTORE_KEYID)?.isNotEmpty()
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
              }
                .onFailure { it.message?.let { message -> activity.snackbar(message = message) } }

              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                activity.getSystemService<ShortcutManager>()?.apply {
                  removeDynamicShortcuts(dynamicShortcuts.map { it.id }.toMutableList())
                }
              }
              activity.sharedPrefs.edit { putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, false) }
              dialogInterface.cancel()
              activity.finish()
            }
            .setNegativeButton(R.string.dialog_do_not_delete) { dialogInterface, _ ->
              run { dialogInterface.cancel() }
            }
            .show()
          true
        }
      }
    }
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface RepositorySettingsEntryPoint {
    fun gitSettings(): GitSettings
    @GitPreferences fun encryptedPreferences(): SharedPreferences
  }
}
