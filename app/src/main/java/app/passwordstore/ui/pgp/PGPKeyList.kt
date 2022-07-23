package app.passwordstore.ui.pgp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.passwordstore.crypto.GpgIdentifier

@Composable
fun KeyList(
  identifiers: List<GpgIdentifier>,
  onItemClick: (identifier: GpgIdentifier) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(modifier = modifier) {
    items(identifiers) { identifier ->
      KeyItem(identifier = identifier, modifier = Modifier.clickable { onItemClick(identifier) })
    }
  }
}

@Composable
private fun KeyItem(
  identifier: GpgIdentifier,
  modifier: Modifier = Modifier,
) {
  val label =
    when (identifier) {
      is GpgIdentifier.KeyId -> identifier.id.toString()
      is GpgIdentifier.UserId -> identifier.email
    }
  Box(modifier = modifier.padding(16.dp).fillMaxWidth()) { Text(text = label) }
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
