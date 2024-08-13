/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.crypto

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.passwordstore.R
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.ui.APSAppBar
import app.passwordstore.ui.compose.PasswordField
import app.passwordstore.ui.compose.theme.APSTheme
import app.passwordstore.ui.compose.theme.SpacingLarge
import app.passwordstore.ui.compose.theme.SpacingMedium
import app.passwordstore.util.time.UserClock
import app.passwordstore.util.totp.UriTotpFinder

/** Composable to show allow editing an existing [PasswordEntry]. */
@Composable
fun EditPasswordScreen(
  entryName: String,
  entry: PasswordEntry,
  onNavigateUp: () -> Unit,
  @Suppress("UNUSED_PARAMETER") onSave: (PasswordEntry) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    topBar = {
      APSAppBar(
        title = entryName,
        navigationIcon = painterResource(R.drawable.ic_arrow_back_black_24dp),
        onNavigationIconClick = onNavigateUp,
        backgroundColor = MaterialTheme.colorScheme.surface,
      )
    }
  ) { paddingValues ->
    Box(modifier = modifier.padding(paddingValues)) {
      Column(
        modifier =
          Modifier.padding(vertical = SpacingMedium, horizontal = SpacingLarge).fillMaxSize()
      ) {
        if (entry.password != null) {
          PasswordField(
            value = entry.password!!,
            label = stringResource(R.string.password),
            initialVisibility = false,
            modifier = Modifier.padding(bottom = SpacingMedium).fillMaxWidth(),
          )
        }
        ExtraContent(entry = entry)
      }
    }
  }
}

@Composable
private fun ExtraContent(entry: PasswordEntry, modifier: Modifier = Modifier) {
  TextField(
    value = entry.extraContentString,
    onValueChange = {},
    label = { Text("Extra content") },
    modifier = modifier.fillMaxWidth(),
  )
}

@Preview
@Composable
private fun EditPasswordScreenPreview() {
  APSTheme {
    EditPasswordScreen(
      entryName = "Test Entry",
      entry = createTestEntry(),
      onNavigateUp = {},
      onSave = {},
    )
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
      .encodeToByteArray(),
  )
