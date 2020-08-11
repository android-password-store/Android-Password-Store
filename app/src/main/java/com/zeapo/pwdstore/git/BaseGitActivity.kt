/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Intent
import android.view.MenuItem
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.Timber.tag
import com.github.ajalt.timberkt.e
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.git.config.ConnectionMode
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.git.config.SshApiSessionFactory
import com.zeapo.pwdstore.git.operation.BreakOutOfDetached
import com.zeapo.pwdstore.git.operation.CloneOperation
import com.zeapo.pwdstore.git.operation.GitOperation
import com.zeapo.pwdstore.git.operation.PullOperation
import com.zeapo.pwdstore.git.operation.PushOperation
import com.zeapo.pwdstore.git.operation.ResetToRemoteOperation
import com.zeapo.pwdstore.git.operation.SyncOperation
import com.zeapo.pwdstore.utils.PasswordRepository
import kotlinx.coroutines.launch

/**
 * Abstract AppCompatActivity that holds some information that is commonly shared across git-related
 * tasks and makes sense to be held here.
 */
abstract class BaseGitActivity : AppCompatActivity() {

    private var identityBuilder: SshApiSessionFactory.IdentityBuilder? = null
    private var identity: SshApiSessionFactory.ApiIdentity? = null

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @CallSuper
    override fun onDestroy() {
        // Do not leak the service connection
        if (identityBuilder != null) {
            identityBuilder!!.close()
            identityBuilder = null
        }
        super.onDestroy()
    }

    /**
     * Attempt to launch the requested Git operation. Depending on the configured auth, it may not
     * be possible to launch the operation immediately. In that case, this function may launch an
     * intermediate activity instead, which will gather necessary information and post it back via
     * onActivityResult, which will then re-call this function. This may happen multiple times,
     * until either an error is encountered or the operation is successfully launched.
     *
     * @param operation The type of git operation to launch
     */
    suspend fun launchGitOperation(operation: Int) {
        if (GitSettings.url == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        try {
            // Before launching the operation with OpenKeychain auth, we need to issue several requests
            // to the OpenKeychain API. IdentityBuild will take care of launching the relevant intents,
            // we just need to keep calling it until it returns a completed ApiIdentity.
            if (GitSettings.connectionMode == ConnectionMode.OpenKeychain && identity == null) {
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

            val localDir = requireNotNull(PasswordRepository.getRepositoryDirectory())
            val op = when (operation) {
                REQUEST_CLONE, GitOperation.GET_SSH_KEY_FROM_CLONE -> CloneOperation(localDir, GitSettings.url!!, this)
                REQUEST_PULL -> PullOperation(localDir, this)
                REQUEST_PUSH -> PushOperation(localDir, this)
                REQUEST_SYNC -> SyncOperation(localDir, this)
                BREAK_OUT_OF_DETACHED -> BreakOutOfDetached(localDir, this)
                REQUEST_RESET -> ResetToRemoteOperation(localDir, this)
                SshApiSessionFactory.POST_SIGNATURE -> return
                else -> {
                    tag(TAG).e { "Operation not recognized : $operation" }
                    setResult(RESULT_CANCELED)
                    finish()
                    return
                }
            }
            op.executeAfterAuthentication(GitSettings.connectionMode, identity)
        } catch (e: Exception) {
            e.printStackTrace()
            MaterialAlertDialogBuilder(this).setMessage(e.message).show()
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // In addition to the pre-operation-launch series of intents for OpenKeychain auth
        // that will pass through here and back to launchGitOperation, there is one
        // synchronous operation that happens /after/ the operation has been launched in the
        // background thread - the actual signing of the SSH challenge. We pass through the
        // completed signature to the ApiIdentity, which will be blocked in the other thread
        // waiting for it.
        if (requestCode == SshApiSessionFactory.POST_SIGNATURE && identity != null) {
            identity!!.postSignature(data)

            // If the signature failed (usually because it was cancelled), reset state
            if (data == null) {
                identity = null
                identityBuilder = null
            }
            return
        }

        if (resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED)
            finish()
        } else if (resultCode == RESULT_OK) {
            // If an operation has been re-queued via this mechanism, let the
            // IdentityBuilder attempt to extract some updated state from the intent before
            // trying to re-launch the operation.
            if (identityBuilder != null) {
                identityBuilder!!.consume(data)
            }
            lifecycleScope.launch { launchGitOperation(requestCode) }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {

        const val REQUEST_ARG_OP = "OPERATION"
        const val REQUEST_PULL = 101
        const val REQUEST_PUSH = 102
        const val REQUEST_CLONE = 103
        const val REQUEST_SYNC = 104
        const val BREAK_OUT_OF_DETACHED = 105
        const val REQUEST_RESET = 106
        const val TAG = "AbstractGitActivity"
    }
}
