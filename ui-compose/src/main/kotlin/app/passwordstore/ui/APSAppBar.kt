package app.passwordstore.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

@Composable
public fun APSAppBar(
  title: String,
  backgroundColor: Color,
  navigationIcon: Painter?,
  onNavigationIconClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  SmallTopAppBar(
    title = { Text(text = title) },
    navigationIcon = {
      if (navigationIcon != null) {
        IconButton(onClick = { onNavigationIconClick?.invoke() }) {
          Icon(
            painter = navigationIcon,
            contentDescription = null,
          )
        }
      }
    },
    colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = backgroundColor),
    modifier = modifier,
  )
}
