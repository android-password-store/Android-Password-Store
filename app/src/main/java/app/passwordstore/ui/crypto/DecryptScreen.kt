package app.passwordstore.ui.crypto

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.passwordstore.R
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.ui.APSAppBar
import app.passwordstore.ui.compose.PasswordField
import app.passwordstore.ui.compose.theme.APSThemePreview
import app.passwordstore.util.time.UserClock
import app.passwordstore.util.totp.UriTotpFinder
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTime::class, ExperimentalMaterial3Api::class)
@Composable
fun PasswordEntryScreen(
  entryName: String,
  entry: PasswordEntry,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    topBar = {
      APSAppBar(
        title = "",
        navigationIcon = painterResource(R.drawable.ic_arrow_back_black_24dp),
        onNavigationIconClick = {},
        backgroundColor = MaterialTheme.colorScheme.surface,
      )
    },
  ) { paddingValues ->
    Box(modifier = modifier.fillMaxSize().padding(paddingValues)) {
      Column(modifier = Modifier.padding(8.dp)) {
        Text(
          text = entryName,
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.padding(bottom = 8.dp),
        )
        if (entry.password != null) {
          PasswordField(
            value = entry.password!!,
            label = "Password",
            initialVisibility = false,
            modifier = Modifier.padding(bottom = 8.dp),
          )
        }
        if (entry.hasTotp()) {
          val totp by entry.totp.collectAsState(runBlocking { entry.totp.first() })
          TextField(
            value = totp.value,
            onValueChange = {},
            readOnly = true,
            label = { Text("OTP (expires in ${totp.remainingTime.inWholeSeconds}s)") },
            trailingIcon = { CopyButton({ totp.value }) },
            modifier = Modifier.padding(bottom = 8.dp),
          )
        }
        if (entry.username != null) {
          TextField(
            value = entry.username!!,
            onValueChange = {},
            readOnly = true,
            label = { Text("Username") },
            trailingIcon = { CopyButton({ entry.username!! }) },
            modifier = Modifier.padding(bottom = 8.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun CopyButton(
  textToCopy: () -> String,
  modifier: Modifier = Modifier,
) {
  val clipboard = LocalClipboardManager.current
  IconButton(
    onClick = { clipboard.setText(AnnotatedString(textToCopy())) },
    modifier = modifier,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_content_copy),
      contentDescription = stringResource(R.string.copy_password),
    )
  }
}

@Preview
@Composable
private fun PasswordEntryPreview() {
  APSThemePreview { PasswordEntryScreen("Test Entry", createTestEntry()) }
}

private fun createTestEntry() =
  PasswordEntry(
    UserClock(),
    UriTotpFinder(),
    """
    |My Password
    |otpauth://totp/ACME%20Co:john@example.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&algorithm=SHA1&digits=6&period=30
    |login: msfjarvis
  """
      .trimMargin()
      .encodeToByteArray()
  )
