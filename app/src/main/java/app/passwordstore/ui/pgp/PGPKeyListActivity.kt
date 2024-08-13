/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.pgp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.passwordstore.R
import app.passwordstore.ui.APSAppBar
import app.passwordstore.ui.compose.theme.APSTheme
import app.passwordstore.util.viewmodel.PGPKeyListViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PGPKeyListActivity : ComponentActivity() {

  private val viewModel: PGPKeyListViewModel by viewModels()
  private val keyImportAction =
    registerForActivityResult(StartActivityForResult()) {
      if (it.resultCode == RESULT_OK) {
        viewModel.updateKeySet()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val isSelecting = intent.extras?.getBoolean(EXTRA_KEY_SELECTION) ?: false
    setContent {
      APSTheme {
        Scaffold(
          topBar = {
            APSAppBar(
              title = stringResource(R.string.activity_label_pgp_key_manager),
              navigationIcon = painterResource(R.drawable.ic_arrow_back_black_24dp),
              onNavigationIconClick = { finish() },
              backgroundColor = MaterialTheme.colorScheme.surface,
            )
          },
          floatingActionButton = {
            FloatingActionButton(
              onClick = { keyImportAction.launch(Intent(this, PGPKeyImportActivity::class.java)) }
            ) {
              Icon(
                painter = painterResource(R.drawable.ic_add_48dp),
                stringResource(R.string.pref_import_pgp_key_title),
              )
            }
          },
        ) { paddingValues ->
          KeyList(
            identifiers = viewModel.keys,
            onItemClick = viewModel::deleteKey,
            modifier = Modifier.padding(paddingValues),
            onKeySelected =
              if (isSelecting) {
                { identifier ->
                  val result = Intent()
                  result.putExtra(EXTRA_SELECTED_KEY, identifier.toString())
                  setResult(RESULT_OK, result)
                  finish()
                }
              } else null,
          )
        }
      }
    }
  }

  companion object {
    const val EXTRA_SELECTED_KEY = "SELECTED_KEY"
    private const val EXTRA_KEY_SELECTION = "KEY_SELECTION_MODE"

    fun newSelectionActivity(context: Context): Intent {
      val intent = Intent(context, PGPKeyListActivity::class.java)
      intent.putExtra(EXTRA_KEY_SELECTION, true)
      return intent
    }
  }
}
