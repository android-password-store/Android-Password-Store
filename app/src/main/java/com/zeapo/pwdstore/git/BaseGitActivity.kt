/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.Protocol
import com.zeapo.pwdstore.git.config.SshApiSessionFactory
import com.zeapo.pwdstore.utils.PasswordRepository
import java.io.File
import timber.log.Timber

/**
 * Abstract AppCompatActivity that holds some information that is commonly shared across git-related
 * tasks and makes sense to be held here.
 */
abstract class BaseGitActivity : AppCompatActivity() {
    lateinit var protocol: Protocol
    lateinit var connectionMode: ConnectionMode
    lateinit var hostname: String
    lateinit var serverUrl: String
    lateinit var serverPort: String
    lateinit var serverUser: String
    lateinit var serverPath: String
    lateinit var username: String
    lateinit var email: String
    var identityBuilder: SshApiSessionFactory.IdentityBuilder? = null
    var identity: SshApiSessionFactory.ApiIdentity? = null
    lateinit var settings: SharedPreferences
        private set

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = PreferenceManager.getDefaultSharedPreferences(this)
        protocol = Protocol.fromString(settings.getString("git_remote_protocol", null))
        connectionMode = ConnectionMode.fromString(settings.getString("git_remote_auth", null))
        serverUrl = settings.getString("git_remote_server", null) ?: ""
        serverPort = settings.getString("git_remote_port", null) ?: ""
        serverUser = settings.getString("git_remote_username", null) ?: ""
        serverPath = settings.getString("git_remote_location", null) ?: ""
        username = settings.getString("git_config_user_name", null) ?: ""
        email = settings.getString("git_config_user_email", null) ?: ""
        updateHostname()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun updateHostname(): Boolean {
        var valid = false
        hostname = when (protocol) {
            Protocol.Ssh -> {
                val hostname = StringBuilder()
                hostname.append("$serverUser@${serverUrl.trim { it <= ' '}}:")
                if (serverPort == "22") {
                    hostname.append(serverPath)
                } else {
                    valid = !(!serverPath.matches("/.*".toRegex()) && serverPort.isNotEmpty())
                    hostname.append(serverPort + serverPath)
                }
                hostname.toString()
            }
            Protocol.Https -> {
                val hostname = StringBuilder()
                hostname.append(serverUrl.trim { it <= ' ' })
                valid = if (serverPort == "443") {
                    hostname.append(serverPath)
                    false
                } else {
                    hostname.append("/")
                            .append(serverPort)
                            .append(serverPath)
                    true
                }
                hostname.toString()
            }
        }
        if (!valid)
            PasswordRepository.addRemote("origin", hostname, true)
        return valid
    }

    /**
     * Attempt to launch the requested GIT operation. Depending on the configured auth, it may not
     * be possible to launch the operation immediately. In that case, this function may launch an
     * intermediate activity instead, which will gather necessary information and post it back via
     * onActivityResult, which will then re-call this function. This may happen multiple times,
     * until either an error is encountered or the operation is successfully launched.
     *
     * @param operation The type of GIT operation to launch
     */
    fun launchGitOperation(operation: Int) {
        val op: GitOperation
        val localDir = requireNotNull(PasswordRepository.getRepositoryDirectory(this))
        try {
            // Before launching the operation with OpenKeychain auth, we need to issue several requests
            // to the OpenKeychain API. IdentityBuild will take care of launching the relevant intents,
            // we just need to keep calling it until it returns a completed ApiIdentity.
            if (connectionMode == ConnectionMode.OpenKeychain && identity == null) {
                // Lazy initialization of the IdentityBuilder
                if (identityBuilder == null) {
                    identityBuilder = SshApiSessionFactory.IdentityBuilder(this)
                }
                // Try to get an ApiIdentity and bail if one is not ready yet. The builder will ensure
                // that onActivityResult is called with operation again, which will re-invoke us here
                identity = identityBuilder!!.tryBuild(operation)
                if (identity == null)
                    return
            }

            op = when (operation) {
                REQUEST_CLONE, GitOperation.GET_SSH_KEY_FROM_CLONE -> CloneOperation(localDir, this).setCommand(hostname)
                REQUEST_PULL -> PullOperation(localDir, this).setCommand()
                REQUEST_PUSH -> PushOperation(localDir, this).setCommand()
                REQUEST_SYNC -> SyncOperation(localDir, this).setCommands()
                BREAK_OUT_OF_DETACHED -> BreakOutOfDetached(localDir, this).setCommands()
                REQUEST_RESET -> ResetToRemoteOperation(localDir, this).setCommands()
                SshApiSessionFactory.POST_SIGNATURE -> return
                else -> {
                    Timber.tag(TAG).e("Operation not recognized : $operation")
                    setResult(RESULT_CANCELED)
                    finish()
                    return
                }
            }
            op.executeAfterAuthentication(connectionMode,
                    settings.getString("git_remote_username", "git")!!,
                    File("$filesDir/.ssh_key"),
                    identity)
        } catch (e: Exception) {
            e.printStackTrace()
            MaterialAlertDialogBuilder(this).setMessage(e.message).show()
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val REQUEST_PULL = 101
        const val REQUEST_PUSH = 102
        const val REQUEST_CLONE = 103
        const val REQUEST_INIT = 104
        const val REQUEST_SYNC = 105
        const val REQUEST_CREATE = 106
        const val BREAK_OUT_OF_DETACHED = 107
        const val REQUEST_RESET = 108
        const val TAG = "AbstractGitActivity"
    }
}
