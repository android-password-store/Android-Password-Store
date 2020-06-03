/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.zeapo.pwdstore.utils.PasswordRepository

// TODO more work needed, this is just an extraction from PgpHandler

class SelectFolderActivity : AppCompatActivity(R.layout.select_folder_layout) {
    private lateinit var passwordList: SelectFolderFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        passwordList = SelectFolderFragment()
        val args = Bundle()
        args.putString(PasswordStore.REQUEST_ARG_PATH, PasswordRepository.getRepositoryDirectory(applicationContext).absolutePath)

        passwordList.arguments = args

        supportActionBar?.show()

        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        supportFragmentManager.commit {
            replace(R.id.pgp_handler_linearlayout, passwordList, "PasswordsList")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.pgp_handler_select_folder, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return true
            }
            R.id.crypto_select -> selectFolder()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectFolder() {
        intent.putExtra("SELECTED_FOLDER_PATH", passwordList.currentDir.absolutePath)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}
