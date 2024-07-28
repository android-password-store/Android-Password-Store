/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.folderselect

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import app.passwordstore.R
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.ui.passwords.PASSWORD_FRAGMENT_TAG
import app.passwordstore.ui.passwords.PasswordStore
import dagger.hilt.android.AndroidEntryPoint
import kotlin.io.path.absolutePathString

@AndroidEntryPoint
class SelectFolderActivity : AppCompatActivity(R.layout.select_folder_layout) {

  private lateinit var passwordList: SelectFolderFragment

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    passwordList = SelectFolderFragment()
    val args = Bundle()
    args.putString(
      PasswordStore.REQUEST_ARG_PATH,
      PasswordRepository.getRepositoryDirectory().absolutePathString(),
    )

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
        onBackPressedDispatcher.onBackPressed()
      }
      R.id.crypto_select -> selectFolder()
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  private fun selectFolder() {
    intent.putExtra("SELECTED_FOLDER_PATH", passwordList.currentDir.absolutePathString())
    setResult(RESULT_OK, intent)
    finish()
  }
}
