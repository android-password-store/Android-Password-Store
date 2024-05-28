package app.passwordstore.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import app.passwordstore.ui.compose.preview.DevicePreviews
import app.passwordstore.ui.compose.preview.ThemePreviews
import app.passwordstore.ui.compose.theme.APSTheme

@Composable
@OptIn(ExperimentalMaterial3Api::class)
public fun APSAppBar(
  title: String,
  backgroundColor: Color,
  navigationIcon: Painter?,
  modifier: Modifier = Modifier,
  onNavigationIconClick: (() -> Unit) = {},
) {
  TopAppBar(
    title = { Text(text = title) },
    navigationIcon = {
      if (navigationIcon != null) {
        IconButton(onClick = { onNavigationIconClick.invoke() }) {
          Icon(
            painter = navigationIcon,
            contentDescription = null,
          )
        }
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
    modifier = modifier.shadow(8.dp),
  )
}

@ThemePreviews
@DevicePreviews
@Composable
private fun APSAppBarPreview() {
  APSTheme {
    APSAppBar(
      title = "Preview",
      backgroundColor = MaterialTheme.colorScheme.surface,
      navigationIcon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
    )
  }
}
