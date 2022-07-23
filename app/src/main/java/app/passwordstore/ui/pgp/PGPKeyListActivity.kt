package app.passwordstore.ui.pgp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
        ) { paddingValues ->
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
