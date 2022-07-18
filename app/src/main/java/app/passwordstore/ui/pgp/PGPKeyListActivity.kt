package app.passwordstore.ui.pgp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.passwordstore.ui.compose.theme.APSTheme
import app.passwordstore.util.viewmodel.PGPKeyListViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class PGPKeyListActivity : ComponentActivity() {

  private val viewModel: PGPKeyListViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      APSTheme {
        Scaffold { paddingValues ->
          PGPKeyList(viewModel = viewModel, modifier = Modifier.padding(paddingValues))
        }
      }
    }
  }
}

@Composable
fun PGPKeyList(
  viewModel: PGPKeyListViewModel,
  modifier: Modifier = Modifier,
) {
  KeyList(
    identifiers = viewModel.keys,
    onItemClick = viewModel::deleteKey,
    modifier = modifier,
  )
}
