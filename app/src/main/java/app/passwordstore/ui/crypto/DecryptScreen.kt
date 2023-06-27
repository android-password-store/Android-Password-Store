package app.passwordstore.ui.crypto

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.passwordstore.R
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.ui.APSAppBar
import app.passwordstore.ui.compose.PasswordField
import app.passwordstore.ui.compose.theme.APSThemePreview
import app.passwordstore.util.time.UserClock
import app.passwordstore.util.totp.UriTotpFinder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Composable to show a [PasswordEntry]. It can be used for both read-only usage (decrypt screen) or
 * read-write (encrypt screen) to allow sharing UI logic for both these screens and deferring all
 * the cryptographic aspects to its parent.
 *
 * When [readOnly] is `true`, the Composable assumes that we're showcasing the provided [entry] to
 * the user and does not offer any edit capabilities.
 *
 * When [readOnly] is `false`, the [TextField]s are rendered editable but currently do not pass up
 * their "updated" state to anything. This will be changed in later commits.
 */
@Composable
fun PasswordEntryScreen(
  entryName: String,
  entry: PasswordEntry,
  readOnly: Boolean,
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
            readOnly = readOnly,
            modifier = Modifier.padding(bottom = 8.dp),
          )
        }
        if (entry.hasTotp() && readOnly) {
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
        if (entry.username != null && readOnly) {
          TextField(
            value = entry.username!!,
            onValueChange = {},
            readOnly = true,
            label = { Text("Username") },
            trailingIcon = { CopyButton({ entry.username!! }) },
            modifier = Modifier.padding(bottom = 8.dp),
          )
        }
        ExtraContent(entry = entry, readOnly = readOnly)
      }
    }
  }
}

@Composable
private fun ExtraContent(
  entry: PasswordEntry,
  readOnly: Boolean,
  modifier: Modifier = Modifier,
) {
  if (readOnly) {
    entry.extraContent.forEach { (label, value) ->
      TextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label.capitalize(Locale.current)) },
        trailingIcon = { CopyButton({ value }) },
        modifier = modifier.padding(bottom = 8.dp),
      )
    }
  } else {
    TextField(
      value = entry.extraContentWithoutAuthData,
      onValueChange = {},
      readOnly = false,
      label = { Text("Extra content") },
      modifier = modifier,
    )
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
  APSThemePreview {
    PasswordEntryScreen(entryName = "Test Entry", entry = createTestEntry(), readOnly = true)
  }
}

private fun createTestEntry() =
  PasswordEntry(
    UserClock(),
    UriTotpFinder(),
    """
    |My Password
    |otpauth://totp/ACME%20Co:john@example.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&algorithm=SHA1&digits=6&period=30
    |login: msfjarvis
    |URL: example.com
  """
      .trimMargin()
      .encodeToByteArray()
  )
