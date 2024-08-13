/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import app.passwordstore.ui.compose.preview.DevicePreviews
import app.passwordstore.ui.compose.preview.ThemePreviews
import app.passwordstore.ui.compose.theme.APSTheme
import app.passwordstore.ui.compose.theme.SpacingLarge

public enum class ItemType {
  File,
  Folder,
}

@Composable
public fun PasswordItem(
  label: String,
  type: ItemType,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .clickable(enabled = true, onClick = onClick)
        .background(MaterialTheme.colorScheme.background)
        .minimumInteractiveComponentSize()
        .padding(horizontal = SpacingLarge)
        .fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      modifier = Modifier.wrapContentWidth(),
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
    when (type) {
      ItemType.File -> {}
      ItemType.Folder -> {
        Icon(
          painter = rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowRight),
          contentDescription = "Folder indicator",
          tint = MaterialTheme.colorScheme.onBackground,
        )
      }
    }
  }
}

@ThemePreviews
@DevicePreviews
@Composable
private fun PasswordItemPreview() {
  APSTheme {
    LazyColumn {
      items(20) {
        PasswordItem(label = "Title $it", type = ItemType.entries.random(), onClick = {})
        HorizontalDivider()
      }
    }
  }
}
