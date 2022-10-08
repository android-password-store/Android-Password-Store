package app.passwordstore.ui.compose

import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PasswordField(
  value: String,
  label: String,
  initialVisibility: Boolean,
  modifier: Modifier = Modifier,
) {
  var visible by remember { mutableStateOf(initialVisibility) }
  TextField(
    value = value,
    onValueChange = {},
    readOnly = true,
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
    Icon(
      painter = icon,
      contentDescription = contentDescription,
    )
  }
}
