package app.passwordstore.ui.pgp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import app.passwordstore.R
import app.passwordstore.ui.APSAppBar
import app.passwordstore.ui.compose.theme.APSTheme
import app.passwordstore.ui.compose.theme.decideColorScheme
import app.passwordstore.util.viewmodel.PGPKeyListViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
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
    setContent {
      val context = LocalContext.current
      APSTheme(colors = decideColorScheme(context)) {
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
                stringResource(R.string.pref_import_pgp_key_title)
              )
            }
          }
        ) { paddingValues ->
          PGPKeyList(viewModel = viewModel, modifier = Modifier.padding(paddingValues))
        }
      }
    }
  }
}

@Composable
fun PGPKeyList(
  modifier: Modifier = Modifier,
  viewModel: PGPKeyListViewModel = viewModel(),
) {
  KeyList(
    identifiers = viewModel.keys,
    onItemClick = viewModel::deleteKey,
    modifier = modifier,
  )
}
