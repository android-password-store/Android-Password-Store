/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.crypto

import android.app.PendingIntent
import android.content.ClipData
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.injection.prefs.SettingsPreferences
import dev.msfjarvis.aps.util.FeatureFlags
import dev.msfjarvis.aps.util.extensions.OPENPGP_PROVIDER
import dev.msfjarvis.aps.util.extensions.asLog
import dev.msfjarvis.aps.util.extensions.clipboard
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.snackbar
import dev.msfjarvis.aps.util.extensions.unsafeLazy
import dev.msfjarvis.aps.util.services.ClipboardService
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import java.io.File
import javax.inject.Inject
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.logcat
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpServiceConnection
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError

@Suppress("Registered")
@AndroidEntryPoint
open class BasePgpActivity : AppCompatActivity(), OpenPgpServiceConnection.OnBound {

  /** Full path to the repository */
  val repoPath by unsafeLazy { intent.getStringExtra("REPO_PATH")!! }

  /** Full path to the password file being worked on */
  val fullPath by unsafeLazy { intent.getStringExtra("FILE_PATH")!! }

  /**
   * Name of the password file
   *
   * Converts personal/auth.foo.org/john_doe@example.org.gpg to john_doe.example.org
   */
  val name: String by unsafeLazy { File(fullPath).nameWithoutExtension }

  /** [SharedPreferences] instance used by subclasses to persist settings */
  @SettingsPreferences @Inject lateinit var settings: SharedPreferences

  /**
   * Handle to the [OpenPgpApi] instance that is used by subclasses to interface with OpenKeychain.
   */
  private var serviceConnection: OpenPgpServiceConnection? = null
  var api: OpenPgpApi? = null

  /**
   * A [OpenPgpServiceConnection.OnBound] instance for the last listener that we wish to bind with
   * in case the previous attempt was cancelled due to missing [OPENPGP_PROVIDER] package.
   */
  private var previousListener: OpenPgpServiceConnection.OnBound? = null

  /**
   * [onCreate] sets the window up with the right flags to prevent auth leaks through screenshots or
   * recent apps screen.
   */
  @CallSuper
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
  }

  /**
   * [onDestroy] handles unbinding from the OpenPgp service linked with [serviceConnection]. This is
   * annotated with [CallSuper] because it's critical to unbind the service to ensure we're not
   * leaking things.
   */
  @CallSuper
  override fun onDestroy() {
    super.onDestroy()
    serviceConnection?.unbindFromService()
    previousListener = null
  }

  /**
   * [onResume] controls the flow for resumption of a PGP operation that was previously interrupted
   * by the [OPENPGP_PROVIDER] package being missing.
   */
  override fun onResume() {
    super.onResume()
    previousListener?.let { bindToOpenKeychain(it) }
  }

  /**
   * Sets up [api] once the service is bound. Downstream consumers must call super this to
   * initialize [api]
   */
  @CallSuper
  override fun onBound(service: IOpenPgpService2) {
    api = OpenPgpApi(this, service)
  }

  /**
   * Mandatory error handling from [OpenPgpServiceConnection.OnBound]. All subclasses must handle
   * their own errors, and hence this class simply logs and rethrows. Subclasses Must NOT call
   * super.
   */
  override fun onError(e: Exception) {
    logcat(ERROR) { e.asLog("Callers must handle their own exceptions") }
    throw e
  }

  /** Method for subclasses to initiate binding with [OpenPgpServiceConnection]. */
  fun bindToOpenKeychain(onBoundListener: OpenPgpServiceConnection.OnBound) {
    if (FeatureFlags.ENABLE_PGP_V2_BACKEND) return
    val installed =
      runCatching {
          packageManager.getPackageInfo(OPENPGP_PROVIDER, 0)
          true
        }
        .getOr(false)
    if (!installed) {
      previousListener = onBoundListener
      MaterialAlertDialogBuilder(this)
        .setTitle(getString(R.string.openkeychain_not_installed_title))
        .setMessage(getString(R.string.openkeychain_not_installed_message))
        .setPositiveButton(getString(R.string.openkeychain_not_installed_google_play)) { _, _ ->
          runCatching {
            val intent =
              Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(getString(R.string.play_deeplink_template, OPENPGP_PROVIDER))
                setPackage("com.android.vending")
              }
            startActivity(intent)
          }
        }
        .setNeutralButton(getString(R.string.openkeychain_not_installed_fdroid)) { _, _ ->
          runCatching {
            val intent =
              Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(getString(R.string.fdroid_deeplink_template, OPENPGP_PROVIDER))
              }
            startActivity(intent)
          }
        }
        .setOnCancelListener { finish() }
        .show()
      return
    } else {
      previousListener = null
      serviceConnection =
        OpenPgpServiceConnection(this, OPENPGP_PROVIDER, onBoundListener).also {
          it.bindToService()
        }
    }
  }

  /**
   * Handle the case where OpenKeychain returns that it needs to interact with the user
   *
   * @param result The intent returned by OpenKeychain
   */
  fun getUserInteractionRequestIntent(result: Intent): IntentSender {
    logcat(INFO) { "RESULT_CODE_USER_INTERACTION_REQUIRED" }
    return result.getParcelableExtra<PendingIntent>(OpenPgpApi.RESULT_INTENT)!!.intentSender
  }

  /**
   * Base handling of OpenKeychain errors based on the error contained in [result]. Subclasses can
   * use this when they want to default to sane error handling.
   */
  fun handleError(result: Intent) {
    val error: OpenPgpError? = result.getParcelableExtra(OpenPgpApi.RESULT_ERROR)
    if (error != null) {
      when (error.errorId) {
        OpenPgpError.NO_OR_WRONG_PASSPHRASE -> {
          snackbar(message = getString(R.string.openpgp_error_wrong_passphrase))
        }
        OpenPgpError.NO_USER_IDS -> {
          snackbar(message = getString(R.string.openpgp_error_no_user_ids))
        }
        else -> {
          snackbar(message = getString(R.string.openpgp_error_unknown, error.message))
          logcat(ERROR) { "onError getErrorId: ${error.errorId}" }
          logcat(ERROR) { "onError getMessage: ${error.message}" }
        }
      }
    }
  }

  /**
   * Copies provided [text] to the clipboard. Shows a [Snackbar] which can be disabled by passing
   * [showSnackbar] as false.
   */
  fun copyTextToClipboard(
    text: String?,
    showSnackbar: Boolean = true,
    @StringRes snackbarTextRes: Int = R.string.clipboard_copied_text
  ) {
    val clipboard = clipboard ?: return
    val clip = ClipData.newPlainText("pgp_handler_result_pm", text)
    clipboard.setPrimaryClip(clip)
    if (showSnackbar) {
      snackbar(message = resources.getString(snackbarTextRes))
    }
  }

  /**
   * Copies a provided [password] string to the clipboard. This wraps [copyTextToClipboard] to hide
   * the default [Snackbar] and starts off an instance of [ClipboardService] to provide a way of
   * clearing the clipboard.
   */
  fun copyPasswordToClipboard(password: String?) {
    copyTextToClipboard(password, showSnackbar = false)

    val clearAfter = settings.getString(PreferenceKeys.GENERAL_SHOW_TIME)?.toIntOrNull() ?: 45

    if (clearAfter != 0) {
      val service =
        Intent(this, ClipboardService::class.java).apply {
          action = ClipboardService.ACTION_START
          putExtra(ClipboardService.EXTRA_NOTIFICATION_TIME, clearAfter)
        }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(service)
      } else {
        startService(service)
      }
      snackbar(message = resources.getString(R.string.clipboard_password_toast_text, clearAfter))
    } else {
      snackbar(message = resources.getString(R.string.clipboard_password_no_clear_toast_text))
    }
  }

  companion object {

    private const val TAG = "APS/BasePgpActivity"
    const val EXTRA_FILE_PATH = "FILE_PATH"
    const val EXTRA_REPO_PATH = "REPO_PATH"

    /** Gets the relative path to the repository */
    fun getRelativePath(fullPath: String, repositoryPath: String): String =
      fullPath.replace(repositoryPath, "").replace("/+".toRegex(), "/")

    /** Gets the Parent path, relative to the repository */
    fun getParentPath(fullPath: String, repositoryPath: String): String {
      val relativePath = getRelativePath(fullPath, repositoryPath)
      val index = relativePath.lastIndexOf("/")
      return "/${relativePath.substring(startIndex = 0, endIndex = index + 1)}/".replace(
        "/+".toRegex(),
        "/"
      )
    }

    /** /path/to/store/social/facebook.gpg -> social/facebook */
    @JvmStatic
    fun getLongName(fullPath: String, repositoryPath: String, basename: String): String {
      var relativePath = getRelativePath(fullPath, repositoryPath)
      return if (relativePath.isNotEmpty() && relativePath != "/") {
        // remove preceding '/'
        relativePath = relativePath.substring(1)
        if (relativePath.endsWith('/')) {
          relativePath + basename
        } else {
          "$relativePath/$basename"
        }
      } else {
        basename
      }
    }
  }
}
