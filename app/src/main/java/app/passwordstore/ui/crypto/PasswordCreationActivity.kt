/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.crypto

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.databinding.PasswordCreationActivityBinding
import app.passwordstore.ui.dialogs.DicewarePasswordGeneratorDialogFragment
import app.passwordstore.ui.dialogs.OtpImportDialogFragment
import app.passwordstore.ui.dialogs.PasswordGeneratorDialogFragment
import app.passwordstore.util.autofill.AutofillPreferences
import app.passwordstore.util.extensions.asLog
import app.passwordstore.util.extensions.base64
import app.passwordstore.util.extensions.commitChange
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.extensions.isInsideRepository
import app.passwordstore.util.extensions.snackbar
import app.passwordstore.util.extensions.unsafeLazy
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.settings.PreferenceKeys
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentIntegrator.QR_CODE
import com.google.zxing.qrcode.QRCodeReader
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Paths
import javax.inject.Inject
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isSameFileAs
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.io.path.writeBytes
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

@AndroidEntryPoint
class PasswordCreationActivity : BasePGPActivity() {

  private val binding by viewBinding(PasswordCreationActivityBinding::inflate)
  @Inject lateinit var passwordEntryFactory: PasswordEntry.Factory

  private val suggestedName by unsafeLazy { intent.getStringExtra(EXTRA_FILE_NAME) }
  private val suggestedUsername by unsafeLazy { intent.getStringExtra(EXTRA_USERNAME) }
  private val suggestedPass by unsafeLazy { intent.getStringExtra(EXTRA_PASSWORD) }
  private val suggestedExtra by unsafeLazy { intent.getStringExtra(EXTRA_EXTRA_CONTENT) }
  private val shouldGeneratePassword by unsafeLazy {
    intent.getBooleanExtra(EXTRA_GENERATE_PASSWORD, false)
  }
  private val editing by unsafeLazy { intent.getBooleanExtra(EXTRA_EDITING, false) }
  private var oldCategory: String? = null
  private var copy: Boolean = false

  private val otpImportAction =
    registerForActivityResult(StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        binding.otpImportButton.isVisible = false
        val intentResult = IntentIntegrator.parseActivityResult(RESULT_OK, result.data)
        val contents = "${intentResult.contents}\n"
        val currentExtras = binding.extraContent.text.toString()
        if (currentExtras.isNotEmpty() && currentExtras.last() != '\n')
          binding.extraContent.append("\n$contents")
        else binding.extraContent.append(contents)
        snackbar(message = getString(R.string.otp_import_success))
      } else {
        snackbar(message = getString(R.string.otp_import_failure_generic))
      }
    }

  private val imageImportAction =
    registerForActivityResult(ActivityResultContracts.GetContent()) { imageUri ->
      if (imageUri == null) {
        snackbar(message = getString(R.string.otp_import_failure_no_selection))
        return@registerForActivityResult
      }
      val bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, imageUri))
            .copy(Bitmap.Config.ARGB_8888, true)
        } else {
          @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
        }
      val intArray = IntArray(bitmap.width * bitmap.height)
      // copy pixel data from the Bitmap into the 'intArray' array
      bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
      val source: LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
      val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

      val reader = QRCodeReader()
      runCatching {
          val result = reader.decode(binaryBitmap)
          val text = result.text
          val currentExtras = binding.extraContent.text.toString()
          if (currentExtras.isNotEmpty() && currentExtras.last() != '\n')
            binding.extraContent.append("\n$text")
          else binding.extraContent.append(text)
          snackbar(message = getString(R.string.otp_import_success))
          binding.otpImportButton.isVisible = false
        }
        .onFailure { snackbar(message = getString(R.string.otp_import_failure_generic)) }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    title = if (editing) getString(R.string.edit_password) else getString(R.string.new_password_title)

    // Set up Jetpack Compose
    setContent {
      PasswordScreen(
        suggestedName = suggestedName,
        suggestedPass = suggestedPass,
        suggestedExtra = suggestedExtra,
        shouldGeneratePassword = shouldGeneratePassword,
        onOtpImportClicked = { handleOtpImport() },  // External function for handling OTP logic
        onGeneratePassword = { generatePassword() }, // Function for password generation
        modifier = Modifier.padding(16.dp) // Padding for the entire screen
      )
    }

    // Keeping non-Compose logic as is, like OTP handling
    // Implement `otpImportButton` handling logic here using Compose if needed
  }


  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun PasswordScreen(
    suggestedName: String?,
    suggestedPass: String?,
    suggestedExtra: String?,
    shouldGeneratePassword: Boolean,
    onOtpImportClicked: () -> Unit,
    onGeneratePassword: () -> Unit,
    modifier: Modifier = Modifier // Added modifier parameter
  ) {
    var name by remember { mutableStateOf(suggestedName ?: "") }
    var password by remember { mutableStateOf(suggestedPass ?: "") }
    var extraContent by remember { mutableStateOf(suggestedExtra ?: "") }
    var generatePassword by remember { mutableStateOf(shouldGeneratePassword) }
    var showDialog by remember { mutableStateOf(false) }
    var generatedPassword by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(16.dp)) { // Use modifier here
      // Filename field
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Filename") }
      )

      // Username field
      var showUsername by remember { mutableStateOf(false) }
      if (showUsername) {
        OutlinedTextField(
          value = name,
          onValueChange = { /* Handle username changes */ },
          label = { Text("Username") }
        )
      }

      // Password field
      OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        trailingIcon = {
          IconButton(onClick = { showDialog = true }) {
            Icon(Icons.Default.Refresh, contentDescription = "Generate Password")
          }
        }
      )

      // Extra Content field
      OutlinedTextField(
        value = extraContent,
        onValueChange = { extraContent = it },
        label = { Text("Extra Content") },
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
      )

      Spacer(modifier = Modifier.height(16.dp))
      // OTP Import Button
      Button(onClick = { onOtpImportClicked() }) {
        Text(text = "Import OTP")
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Generate Password button
      if (!generatePassword) {
        Button(onClick = { showDialog = true }) {
          Text(text = "Generate Password")
        }
      }

      // Display the generated password
      if (generatedPassword.isNotEmpty()) {
        Text(text = "Generated Password: $generatedPassword", modifier = Modifier.padding(top = 16.dp))
      }

      // Password Generation Dialog
      if (showDialog) {
        PasswordGenerationDialog(
          onDismiss = { showDialog = false },
          onGenerateClick = { newPassword ->
            generatedPassword = newPassword
          },
          onOkClick = { newPassword ->
            password = newPassword // Update the password state
            showDialog = false
          }
        )
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun PasswordGenerationDialog(
    onDismiss: () -> Unit,
    onGenerateClick: (String) -> Unit,
    onOkClick: (String) -> Unit
  ) {
    var length by remember { mutableStateOf("5") }
    var separator by remember { mutableStateOf("-") }
    var generatedPassword by remember { mutableStateOf("chain-geologic-chokehold-re-occupy-cuddly") }
    var selectedPasswordType by remember { mutableStateOf("XKPasswd") }

    // Use a standard List instead of ImmutableList
    val passwordTypes = listOf("XKPasswd", "Alphanumeric")

    AlertDialog(
      onDismissRequest = onDismiss,
      title = {
        Text(text = "Generate Password")
      },
      text = {
        Column {
          Text(text = generatedPassword)
          Spacer(modifier = Modifier.height(16.dp))

          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Type")
            Spacer(modifier = Modifier.width(8.dp))
            DropdownWithOptions(
              selectedOption = selectedPasswordType,
              options = passwordTypes // Changed to standard List
            ) { selectedPasswordType = it }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Length")
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
              value = length,
              onValueChange = { length = it },
              modifier = Modifier.weight(1f),
              keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          if (selectedPasswordType == "XKPasswd") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
              Text(text = "Separator")
              Spacer(modifier = Modifier.width(8.dp))
              TextField(
                value = separator,
                onValueChange = { separator = it },
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = {
          onOkClick(generatedPassword) // Pass the generated password back
        }) {
          Text(text = "OK")
        }
      },
      dismissButton = {
        Row {
          Button(onClick = onDismiss) {
            Text(text = "Cancel")
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(onClick = {
            generatedPassword = when (selectedPasswordType) {
              "XKPasswd" -> generateXKPasswd(length.toInt(), separator)
              else -> generateAlphanumericPassword(length.toInt())
            }
            onGenerateClick(generatedPassword) // Optional: track the generated password (can be removed if you want)
          }) {
            Text(text = "Generate")
          }
        }
      }
    )
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun DropdownWithOptions(
    selectedOption: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
  ) {
    var expanded by remember { mutableStateOf(false) }

    Box {
      // The TextField which shows the selected option
      TextField(
        value = selectedOption,
        onValueChange = {},
        modifier = Modifier
          .fillMaxWidth()
          .clickable { expanded = true }, // Expand the menu on click
        enabled = false,
        trailingIcon = {
          Icon(Icons.Filled.ArrowDropDown, contentDescription = "Expand options")
        },
        colors = TextFieldDefaults.colors(disabledTextColor = Color.Black)
      )

      // DropdownMenu from Jetpack Compose Material3
      DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
      ) {
        // Populate DropdownMenu with items
        options.forEach { option ->
          DropdownMenuItem(
            text = { Text(option) },
            onClick = {
              onOptionSelected(option)  // Invoke callback to handle the selected option
              expanded = false  // Close the dropdown after selecting an option
            }
          )
        }
      }
    }
  }



  private fun generateXKPasswd(length: Int, separator: String): String {
    val wordList = listOf(
      "apple", "banana", "cherry", "date", "elderberry",
      "fig", "grape", "honeydew", "kiwi", "lemon",
      "mango", "nectarine", "orange", "papaya", "quince",
      "raspberry", "strawberry", "tangerine", "ugli", "vanilla",
      "watermelon", "xigua", "yuzu", "zucchini"
    )
    return (1..length)
      .map { wordList[Random.nextInt(wordList.size)] }
      .joinToString(separator)
  }

  private fun generateAlphanumericPassword(length: Int): String {
    val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return (1..length)
      .map { charset[Random.nextInt(charset.length)] }
      .joinToString("")
  }




  private fun handleOtpImport() {
    supportFragmentManager.setFragmentResultListener(OTP_RESULT_REQUEST_KEY, this) { requestKey, bundle ->
      if (requestKey == OTP_RESULT_REQUEST_KEY) {
        val contents = bundle.getString(RESULT)
        val currentExtras = binding.extraContent.text.toString()
        if (currentExtras.isNotEmpty() && currentExtras.last() != '\n') {
          binding.extraContent.append("\n$contents")
        } else {
          binding.extraContent.append(contents)
        }
      }
    }

    val hasCamera = packageManager?.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) == true
    if (hasCamera) {
      val items = arrayOf(
        getString(R.string.otp_import_qr_code),
        getString(R.string.otp_import_from_file),
        getString(R.string.otp_import_manual_entry)
      )
      MaterialAlertDialogBuilder(this)
        .setItems(items) { _, index ->
          when (index) {
            0 -> otpImportAction.launch(
              IntentIntegrator(this)
                .setOrientationLocked(false)
                .setBeepEnabled(false)
                .setDesiredBarcodeFormats(QR_CODE)
                .createScanIntent()
            )
            1 -> imageImportAction.launch("image/*")
            2 -> OtpImportDialogFragment().show(supportFragmentManager, "OtpImport")
          }
        }
        .show()
    } else {
      OtpImportDialogFragment().show(supportFragmentManager, "OtpImport")
    }
  }


  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.pgp_handler_new_password, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        setResult(RESULT_CANCELED)
        onBackPressedDispatcher.onBackPressed()
      }
      R.id.save_password -> {
        copy = false
        requireKeysExist { encrypt() }
      }
      R.id.save_and_copy_password -> {
        copy = true
        requireKeysExist { encrypt() }
      }
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  private fun generatePassword() {
    supportFragmentManager.setFragmentResultListener(PASSWORD_RESULT_REQUEST_KEY, this) {
      requestKey,
      bundle ->
      if (requestKey == PASSWORD_RESULT_REQUEST_KEY) {
        binding.password.setText(bundle.getString(RESULT))
      }
    }
    when (settings.getString(PreferenceKeys.PREF_KEY_PWGEN_TYPE) ?: KEY_PWGEN_TYPE_DICEWARE) {
      KEY_PWGEN_TYPE_CLASSIC ->
        PasswordGeneratorDialogFragment().show(supportFragmentManager, "generator")
      KEY_PWGEN_TYPE_DICEWARE ->
        DicewarePasswordGeneratorDialogFragment().show(supportFragmentManager, "generator")
    }
  }

  private fun updateViewState() =
    with(binding) {
      encryptUsername.apply {
        if (visibility != View.VISIBLE) return@apply
        val hasUsernameInFileName = filename.text.toString().isNotBlank()
        val usernameIsEncrypted = username.text.toString().isNotEmpty()
        isEnabled = hasUsernameInFileName xor usernameIsEncrypted
        isChecked = usernameIsEncrypted
      }
      // Use PasswordEntry to parse extras for OTP
      val entry =
        passwordEntryFactory.create("PLACEHOLDER\n${extraContent.text}".encodeToByteArray())
      otpImportButton.isVisible = !entry.hasTotp()
    }

  /** Encrypts the password and the extra content */
  private fun encrypt() {
    with(binding) {
      val oldName = suggestedName
      val editName = filename.text.toString().trim()
      var editUsername = username.text.toString()
      val editPass = password.text.toString()
      val editExtra = extraContent.text.toString()

      if (editName.isEmpty()) {
        snackbar(message = resources.getString(R.string.file_toast_text))
        return@with
      } else if (editName.contains('/')) {
        snackbar(message = resources.getString(R.string.invalid_filename_text))
        return@with
      }

      if (!editUsername.isEmpty()) {
        editUsername = "\nusername:$editUsername"
      }

      if (editPass.isEmpty() && editExtra.isEmpty()) {
        snackbar(message = resources.getString(R.string.empty_toast_text))
        return@with
      }

      if (copy) {
        copyPasswordToClipboard(editPass)
      }

      // pass enters the key ID into `.gpg-id`.
      val gpgIdentifiers = getPGPIdentifiers(directory.text.toString()) ?: return@with
      val content = "$editPass$editUsername\n$editExtra"
      val path =
        when {
          // If we allowed the user to edit the relative path, we have to consider it here
          // instead of fullPath.
          directoryInputLayout.isEnabled -> {
            val editRelativePath = directory.text.toString().trim()
            if (editRelativePath.isEmpty()) {
              snackbar(message = resources.getString(R.string.path_toast_text))
              return
            }
            val passwordDirectory = Paths.get(repoPath, editRelativePath.trim('/'))
            passwordDirectory.createDirectories()
            if (!passwordDirectory.exists()) {
              snackbar(
                message =
                  "Failed to create directory ${passwordDirectory.relativeTo(Paths.get(repoPath)).pathString}"
              )
              return
            }

            "${passwordDirectory.pathString}/$editName.gpg"
          }
          else -> "$fullPath/$editName.gpg"
        }

      lifecycleScope.launch(dispatcherProvider.main()) {
        runCatching {
            val result =
              withContext(dispatcherProvider.io()) {
                val outputStream = ByteArrayOutputStream()
                repository.encrypt(gpgIdentifiers, content.byteInputStream(), outputStream)
                outputStream
              }
            val passwordFile = Paths.get(path)
            // If we're not editing, this file should not already exist!
            // Additionally, if we were editing and the incoming and outgoing
            // filenames differ, it means we renamed. Ensure that the target
            // doesn't already exist to prevent an accidental overwrite.
            if (
              (!editing || (editing && suggestedName != passwordFile.nameWithoutExtension)) &&
                passwordFile.exists()
            ) {
              snackbar(message = getString(R.string.password_creation_duplicate_error))
              return@runCatching
            }

            if (!passwordFile.toFile().isInsideRepository()) {
              snackbar(message = getString(R.string.message_error_destination_outside_repo))
              return@runCatching
            }

            withContext(dispatcherProvider.io()) { passwordFile.writeBytes(result.toByteArray()) }

            // associate the new password name with the last name's timestamp in history
            val preference = getSharedPreferences("recent_password_history", Context.MODE_PRIVATE)
            val oldFilePathHash = "$repoPath/${oldCategory?.trim('/')}/$suggestedName.gpg".base64()
            val timestamp = preference.getString(oldFilePathHash)
            if (timestamp != null) {
              preference.edit {
                remove(oldFilePathHash)
                putString(passwordFile.absolutePathString().base64(), timestamp)
              }
            }

            val returnIntent = Intent()
            returnIntent.putExtra(RETURN_EXTRA_CREATED_FILE, path)
            returnIntent.putExtra(RETURN_EXTRA_NAME, editName)
            returnIntent.putExtra(RETURN_EXTRA_LONG_NAME, getLongName(fullPath, repoPath, editName))

            if (shouldGeneratePassword) {
              val directoryStructure = AutofillPreferences.directoryStructure(applicationContext)
              val entry = passwordEntryFactory.create(content.encodeToByteArray())
              returnIntent.putExtra(RETURN_EXTRA_PASSWORD, entry.password)
              val username =
                entry.username ?: directoryStructure.getUsernameFor(passwordFile.toFile())
              returnIntent.putExtra(RETURN_EXTRA_USERNAME, username)
            }

            if (
              directoryInputLayout.isVisible &&
                directoryInputLayout.isEnabled &&
                oldName != editName
            ) {
              val oldPath = Paths.get(repoPath, oldCategory?.trim('/') ?: "", "$oldName.gpg")
              if (!oldPath.isSameFileAs(passwordFile) && !oldPath.deleteIfExists()) {
                setResult(RESULT_CANCELED)
                MaterialAlertDialogBuilder(this@PasswordCreationActivity)
                  .setTitle(R.string.password_creation_file_fail_title)
                  .setMessage(
                    getString(R.string.password_creation_file_delete_fail_message, oldName)
                  )
                  .setCancelable(false)
                  .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                  .show()
                return@runCatching
              }
            }

            val commitMessageRes =
              if (editing) R.string.git_commit_edit_text else R.string.git_commit_add_text
            lifecycleScope.launch {
              commitChange(
                  resources.getString(commitMessageRes, getLongName(fullPath, repoPath, editName))
                )
                .onSuccess {
                  setResult(RESULT_OK, returnIntent)
                  finish()
                }
            }
          }
          .onFailure { e ->
            if (e is IOException) {
              logcat(ERROR) { e.asLog("Failed to write password file") }
              setResult(RESULT_CANCELED)
              MaterialAlertDialogBuilder(this@PasswordCreationActivity)
                .setTitle(getString(R.string.password_creation_file_fail_title))
                .setMessage(getString(R.string.password_creation_file_write_fail_message))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .show()
            } else {
              logcat(ERROR) { e.asLog() }
            }
          }
      }
    }
  }

  companion object {

    private const val KEY_PWGEN_TYPE_CLASSIC = "classic"
    private const val KEY_PWGEN_TYPE_DICEWARE = "diceware"
    const val PASSWORD_RESULT_REQUEST_KEY = "PASSWORD_GENERATOR"
    const val OTP_RESULT_REQUEST_KEY = "OTP_IMPORT"
    const val RESULT = "RESULT"
    const val RETURN_EXTRA_CREATED_FILE = "CREATED_FILE"
    const val RETURN_EXTRA_NAME = "NAME"
    const val RETURN_EXTRA_LONG_NAME = "LONG_NAME"
    const val RETURN_EXTRA_USERNAME = "USERNAME"
    const val RETURN_EXTRA_PASSWORD = "PASSWORD"
    const val EXTRA_FILE_NAME = "FILENAME"
    const val EXTRA_USERNAME = "USERNAME"
    const val EXTRA_PASSWORD = "PASSWORD"
    const val EXTRA_EXTRA_CONTENT = "EXTRA_CONTENT"
    const val EXTRA_GENERATE_PASSWORD = "GENERATE_PASSWORD"
    const val EXTRA_EDITING = "EDITING"
  }
}
