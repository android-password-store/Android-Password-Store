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
import com.zeapo.pwdstore.utils.PasswordRepository

open class GitOperationActivity : BaseGitActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.extras?.getInt(REQUEST_ARG_OP)) {
            REQUEST_PULL -> syncRepository(REQUEST_PULL)
            REQUEST_PUSH -> syncRepository(REQUEST_PUSH)
            REQUEST_SYNC -> syncRepository(REQUEST_SYNC)
            else -> {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
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
        if (serverUser.isEmpty() || serverHostname.isEmpty() || url.isNullOrEmpty())
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
            PasswordRepository.addRemote("origin", url!!, true)
            launchGitOperation(operation)
        }
    }
}
