package app.passwordstore.ui.pgp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.passwordstore.R
import app.passwordstore.crypto.GpgIdentifier

@Composable
fun KeyList(
  identifiers: List<GpgIdentifier>,
  onItemClick: (identifier: GpgIdentifier) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(modifier = modifier) {
    items(identifiers) { identifier -> KeyItem(identifier = identifier, onItemClick = onItemClick) }
  }
}

@Composable
private fun KeyItem(
  identifier: GpgIdentifier,
  onItemClick: (identifier: GpgIdentifier) -> Unit,
  modifier: Modifier = Modifier,
) {
  var isDeleting by remember { mutableStateOf(false) }
  if (isDeleting) {
    AlertDialog(
      onDismissRequest = { isDeleting = false },
      title = {
        Text(text = stringResource(R.string.pgp_key_manager_delete_confirmation_dialog_title))
      },
      confirmButton = {
        TextButton(
          onClick = {
            onItemClick(identifier)
            isDeleting = false
          }
        ) {
          Text(text = stringResource(R.string.dialog_yes))
        }
      },
      dismissButton = {
        TextButton(onClick = { isDeleting = false }) {
          Text(text = stringResource(R.string.dialog_no))
        }
      },
    )
  }
  val label =
    when (identifier) {
      is GpgIdentifier.KeyId -> identifier.id.toString()
      is GpgIdentifier.UserId -> identifier.email
    }
  Row(
    modifier = modifier.padding(16.dp).fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(text = label)
    IconButton(onClick = { isDeleting = true }) {
      Icon(
        painter = painterResource(R.drawable.ic_delete_24dp),
        stringResource(id = R.string.delete)
      )
    }
  }
}

@Preview
@Composable
private fun KeyListPreview() {
  KeyList(
    identifiers =
      listOfNotNull(
        GpgIdentifier.fromString("john.doe@example.com"),
        GpgIdentifier.fromString("0xB950AE2813841585")
      ),
    onItemClick = {}
  )
}
