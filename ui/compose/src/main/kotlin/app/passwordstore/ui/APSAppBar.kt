package app.passwordstore.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
public fun APSAppBar(
  title: String,
  backgroundColor: Color,
  navigationIcon: Painter?,
  onNavigationIconClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  TopAppBar(
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
    colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
    modifier = modifier.shadow(8.dp),
  )
}
