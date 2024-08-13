/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.pgp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import app.passwordstore.R
import app.passwordstore.crypto.PGPIdentifier
import app.passwordstore.ui.compose.theme.APSTheme
import app.passwordstore.ui.compose.theme.SpacingLarge
import app.passwordstore.util.extensions.conditional
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

@Composable
fun KeyList(
  identifiers: ImmutableList<PGPIdentifier>,
  onItemClick: (identifier: PGPIdentifier) -> Unit,
  modifier: Modifier = Modifier,
  onKeySelected: ((identifier: PGPIdentifier) -> Unit)? = null,
) {
  if (identifiers.isEmpty()) {
    Column(
      modifier = modifier.fillMaxSize(),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_launcher_foreground),
        contentDescription = "Password Store logo",
      )
      Text(stringResource(R.string.pgp_key_manager_no_keys_guidance))
    }
  } else {
    LazyColumn(modifier = modifier) {
      items(identifiers) { identifier ->
        KeyItem(identifier = identifier, onItemClick = onItemClick, onKeySelected = onKeySelected)
      }
    }
  }
}

@Composable
private fun KeyItem(
  identifier: PGPIdentifier,
  onItemClick: (identifier: PGPIdentifier) -> Unit,
  modifier: Modifier = Modifier,
  onKeySelected: ((identifier: PGPIdentifier) -> Unit)? = null,
) {
  var isDeleting by remember { mutableStateOf(false) }
  DeleteConfirmationDialog(
    isDeleting = isDeleting,
    onDismiss = { isDeleting = false },
    onConfirm = {
      onItemClick(identifier)
      isDeleting = false
    },
  )
  val label =
    when (identifier) {
      is PGPIdentifier.KeyId -> identifier.id.toString()
      is PGPIdentifier.UserId -> identifier.email
    }
  Row(
    modifier =
      modifier.padding(SpacingLarge).fillMaxWidth().conditional(onKeySelected != null) {
        clickable { onKeySelected?.invoke(identifier) }
      },
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      modifier = Modifier.weight(1f),
      overflow = TextOverflow.Ellipsis,
      maxLines = 1,
    )
    if (onKeySelected == null) {
      IconButton(onClick = { isDeleting = true }) {
        Icon(
          painter = painterResource(R.drawable.ic_delete_24dp),
          stringResource(id = R.string.delete),
        )
      }
    }
  }
}

@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun DeleteConfirmationDialog(
  isDeleting: Boolean,
  noinline onDismiss: () -> Unit,
  noinline onConfirm: () -> Unit,
) {
  if (isDeleting) {
    AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(text = stringResource(R.string.pgp_key_manager_delete_confirmation_dialog_title))
      },
      confirmButton = {
        TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.dialog_yes)) }
      },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.dialog_no)) }
      },
    )
  }
}

@Preview
@Composable
private fun KeyListPreview() {
  APSTheme {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
      KeyList(
        identifiers =
          listOfNotNull(
              PGPIdentifier.fromString("ultramicroscopicsilicovolcanoconiosis@example.com"),
              PGPIdentifier.fromString("0xB950AE2813841585"),
            )
            .toPersistentList(),
        onItemClick = {},
      )
    }
  }
}

@Preview
@Composable
private fun EmptyKeyListPreview() {
  APSTheme {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
      KeyList(identifiers = persistentListOf(), onItemClick = {})
    }
  }
}
