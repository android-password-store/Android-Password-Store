package app.passwordstore.ui.crypto

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.passwordstore.R
import app.passwordstore.data.passfile.PasswordEntry
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
  val clipboard = LocalClipboardManager.current
  Box(modifier = modifier.fillMaxSize()) {
    Column {
      Text(entryName)
      if (entry.password != null) {
        TextField(
          value = entry.password!!,
          onValueChange = {},
          readOnly = true,
          label = { Text("Password") },
          trailingIcon = { CopyButton { clipboard.setText(AnnotatedString(entry.password!!)) } },
        )
      }
      if (entry.hasTotp()) {
        val totp by entry.totp.collectAsState(runBlocking { entry.totp.first() })
        TextField(
          value = totp.value,
          onValueChange = {},
          readOnly = true,
          label = { Text("OTP (expires in ${totp.remainingTime.inWholeSeconds}s)") },
          trailingIcon = { CopyButton { clipboard.setText(AnnotatedString(totp.value)) } }
        )
      }
    }
  }
}

@Composable
private fun CopyButton(onClick: () -> Unit) {
  IconButton(
    onClick = onClick,
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
  PasswordEntryScreen("Test Entry", createTestEntry())
}

fun createTestEntry() =
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
