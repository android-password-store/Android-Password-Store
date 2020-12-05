/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.folderselect

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.data.repo.PasswordRepository
import dev.msfjarvis.aps.ui.passwords.PASSWORD_FRAGMENT_TAG
import dev.msfjarvis.aps.ui.passwords.PasswordStore


class SelectFolderActivity : AppCompatActivity(R.layout.select_folder_layout) {

    private lateinit var passwordList: SelectFolderFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        passwordList = SelectFolderFragment()
        val args = Bundle()
        args.putString(PasswordStore.REQUEST_ARG_PATH, PasswordRepository.getRepositoryDirectory().absolutePath)

        passwordList.arguments = args

        supportActionBar?.show()

        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        supportFragmentManager.commit {
            replace(R.id.pgp_handler_linearlayout, passwordList, PASSWORD_FRAGMENT_TAG)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.pgp_handler_select_folder, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                setResult(RESULT_CANCELED)
                finish()
                return true
            }
            R.id.crypto_select -> selectFolder()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectFolder() {
        intent.putExtra("SELECTED_FOLDER_PATH", passwordList.currentDir.absolutePath)
        setResult(RESULT_OK, intent)
        finish()
    }
}
