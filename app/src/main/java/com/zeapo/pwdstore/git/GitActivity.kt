/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.UserPreference
import com.zeapo.pwdstore.git.config.SshApiSessionFactory
import com.zeapo.pwdstore.utils.PasswordRepository

open class GitActivity : BaseGitActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val operationCode = intent.extras!!.getInt("Operation")

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        when (operationCode) {
            REQUEST_PULL -> syncRepository(REQUEST_PULL)

            REQUEST_PUSH -> syncRepository(REQUEST_PUSH)

            REQUEST_SYNC -> syncRepository(REQUEST_SYNC)
        }
    }

    public override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        // Do not leak the service connection
        if (identityBuilder != null) {
            identityBuilder!!.close()
            identityBuilder = null
        }
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.git_clone, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.user_pref -> try {
                val intent = Intent(this, UserPreference::class.java)
                startActivity(intent)
                true
            } catch (e: Exception) {
                println("Exception caught :(")
                e.printStackTrace()
                false
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Syncs the local repository with the remote one (either pull or push)
     *
     * @param operation the operation to execute can be REQUEST_PULL or REQUEST_PUSH
     */
    private fun syncRepository(operation: Int) {
        if (serverUser.isEmpty() || serverUrl.isEmpty() || hostname.isEmpty())
            MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.set_information_dialog_text))
                    .setPositiveButton(getString(R.string.dialog_positive)) { _, _ ->
                        val intent = Intent(this, UserPreference::class.java)
                        startActivityForResult(intent, REQUEST_PULL)
                    }
                    .setNegativeButton(getString(R.string.dialog_negative)) { _, _ ->
                        // do nothing :(
                        setResult(RESULT_OK)
                        finish()
                    }
                    .show()
        else {
            // check that the remote origin is here, else add it
            PasswordRepository.addRemote("origin", hostname, true)
            launchGitOperation(operation)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

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
            launchGitOperation(requestCode)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}
