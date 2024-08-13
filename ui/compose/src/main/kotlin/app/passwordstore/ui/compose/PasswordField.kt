/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.compose

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable
public fun PasswordField(
  value: String,
  label: String,
  initialVisibility: Boolean,
  modifier: Modifier = Modifier,
  readOnly: Boolean = false,
) {
  var visible by remember { mutableStateOf(initialVisibility) }
  TextField(
    value = value,
    onValueChange = {},
    readOnly = readOnly,
    label = { Text(label) },
    visualTransformation =
      if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    trailingIcon = {
      ToggleButton(
        visible = visible,
        contentDescription = "Toggle password visibility",
        onButtonClick = { visible = !visible },
      )
    },
    modifier = modifier,
  )
}

@Composable
private fun ToggleButton(
  visible: Boolean,
  contentDescription: String,
  onButtonClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  IconButton(onClick = onButtonClick, modifier = modifier) {
    val icon =
      if (visible) painterResource(id = R.drawable.baseline_visibility_off_24)
      else painterResource(id = R.drawable.baseline_visibility_24)
    Icon(painter = icon, contentDescription = contentDescription)
  }
}

@Composable
public fun CopyButton(
  textToCopy: String,
  @StringRes buttonLabelRes: Int,
  modifier: Modifier = Modifier,
) {
  val clipboard = LocalClipboardManager.current
  IconButton(onClick = { clipboard.setText(AnnotatedString(textToCopy)) }, modifier = modifier) {
    Icon(
      painter = painterResource(R.drawable.ic_content_copy),
      contentDescription = stringResource(buttonLabelRes),
    )
  }
}
