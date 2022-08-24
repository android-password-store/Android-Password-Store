/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
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
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import app.passwordstore.R
import app.passwordstore.crypto.GpgIdentifier
import app.passwordstore.data.crypto.CryptoRepository
import app.passwordstore.data.passfile.PasswordEntry
import app.passwordstore.data.repo.PasswordRepository
import app.passwordstore.databinding.PasswordCreationActivityBinding
import app.passwordstore.ui.dialogs.DicewarePasswordGeneratorDialogFragment
import app.passwordstore.ui.dialogs.OtpImportDialogFragment
import app.passwordstore.ui.dialogs.PasswordGeneratorDialogFragment
import app.passwordstore.util.autofill.AutofillPreferences
import app.passwordstore.util.autofill.DirectoryStructure
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
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

@AndroidEntryPoint
class PasswordCreationActivity : BasePgpActivity() {

  private val binding by viewBinding(PasswordCreationActivityBinding::inflate)
  @Inject lateinit var passwordEntryFactory: PasswordEntry.Factory
  @Inject lateinit var repository: CryptoRepository

  private val suggestedName by unsafeLazy { intent.getStringExtra(EXTRA_FILE_NAME) }
  private val suggestedPass by unsafeLazy { intent.getStringExtra(EXTRA_PASSWORD) }
  private val suggestedExtra by unsafeLazy { intent.getStringExtra(EXTRA_EXTRA_CONTENT) }
  private val shouldGeneratePassword by unsafeLazy {
    intent.getBooleanExtra(EXTRA_GENERATE_PASSWORD, false)
  }
  private val editing by unsafeLazy { intent.getBooleanExtra(EXTRA_EDITING, false) }
  private val oldFileName by unsafeLazy { intent.getStringExtra(EXTRA_FILE_NAME) }
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
        snackbar(message = getString(R.string.otp_import_failure))
      }
    }

  private val imageImportAction =
    registerForActivityResult(ActivityResultContracts.GetContent()) { imageUri ->
      if (imageUri == null) {
        snackbar(message = getString(R.string.otp_import_failure))
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
        .onFailure { snackbar(message = getString(R.string.otp_import_failure)) }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    title =
      if (editing) getString(R.string.edit_password) else getString(R.string.new_password_title)
    with(binding) {
      setContentView(root)
      generatePassword.setOnClickListener { generatePassword() }
      otpImportButton.setOnClickListener {
        supportFragmentManager.setFragmentResultListener(
          OTP_RESULT_REQUEST_KEY,
          this@PasswordCreationActivity
        ) { requestKey, bundle ->
          if (requestKey == OTP_RESULT_REQUEST_KEY) {
            val contents = bundle.getString(RESULT)
            val currentExtras = binding.extraContent.text.toString()
            if (currentExtras.isNotEmpty() && currentExtras.last() != '\n')
              binding.extraContent.append("\n$contents")
            else binding.extraContent.append(contents)
          }
        }
        val hasCamera = packageManager?.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) == true
        if (hasCamera) {
          val items =
            arrayOf(
              getString(R.string.otp_import_qr_code),
              getString(R.string.otp_import_from_file),
              getString(R.string.otp_import_manual_entry),
            )
          MaterialAlertDialogBuilder(this@PasswordCreationActivity)
            .setItems(items) { _, index ->
              when (index) {
                0 ->
                  otpImportAction.launch(
                    IntentIntegrator(this@PasswordCreationActivity)
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

      directoryInputLayout.apply {
        if (suggestedName != null || suggestedPass != null || shouldGeneratePassword) {
          isEnabled = true
        } else {
          setBackgroundColor(getColor(android.R.color.transparent))
        }
        val path = getRelativePath(fullPath, repoPath)
        // Keep empty path field visible if it is editable.
        if (path.isEmpty() && !isEnabled) visibility = View.GONE
        else {
          directory.setText(path)
          oldCategory = path
        }
      }
      if (suggestedName != null) {
        filename.setText(suggestedName)
      } else {
        filename.requestFocus()
      }
      // Allow the user to quickly switch between storing the username as the filename or
      // in the encrypted extras. This only makes sense if the directory structure is
      // FileBased.
      if (
        suggestedName == null &&
          AutofillPreferences.directoryStructure(this@PasswordCreationActivity) ==
            DirectoryStructure.FileBased
      ) {
        encryptUsername.apply {
          visibility = View.VISIBLE
          setOnClickListener {
            if (isChecked) {
              // User wants to enable username encryption, so we add it to the
              // encrypted extras as the first line.
              val username = filename.text.toString()
              val extras = "username:$username\n${extraContent.text}"

              filename.text?.clear()
              extraContent.setText(extras)
            } else {
              // User wants to disable username encryption, so we extract the
              // username from the encrypted extras and use it as the filename.
              val entry =
                passwordEntryFactory.create("PASSWORD\n${extraContent.text}".encodeToByteArray())
              val username = entry.username

              // username should not be null here by the logic in
              // updateViewState, but it could still happen due to
              // input lag.
              if (username != null) {
                filename.setText(username)
                extraContent.setText(entry.extraContentWithoutAuthData)
              }
            }
          }
        }
      }
      suggestedPass?.let {
        password.setText(it)
        password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
      }
      suggestedExtra?.let { extraContent.setText(it) }
      if (shouldGeneratePassword) {
        generatePassword()
        password.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
      }
    }
    listOf(binding.filename, binding.extraContent).forEach {
      it.doAfterTextChanged { updateViewState() }
    }
    updateViewState()
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
        encrypt()
      }
      R.id.save_and_copy_password -> {
        copy = true
        encrypt()
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
    when (settings.getString(PreferenceKeys.PREF_KEY_PWGEN_TYPE) ?: KEY_PWGEN_TYPE_CLASSIC) {
      KEY_PWGEN_TYPE_CLASSIC ->
        PasswordGeneratorDialogFragment().show(supportFragmentManager, "generator")
      KEY_PWGEN_TYPE_DICEWARE ->
        DicewarePasswordGeneratorDialogFragment().show(supportFragmentManager, "generator")
    }
  }

  private fun updateViewState() =
    with(binding) {
      // Use PasswordEntry to parse extras for username
      val entry =
        passwordEntryFactory.create("PLACEHOLDER\n${extraContent.text}".encodeToByteArray())
      encryptUsername.apply {
        if (visibility != View.VISIBLE) return@apply
        val hasUsernameInFileName = filename.text.toString().isNotBlank()
        val hasUsernameInExtras = !entry.username.isNullOrBlank()
        isEnabled = hasUsernameInFileName xor hasUsernameInExtras
        isChecked = hasUsernameInExtras
      }
      otpImportButton.isVisible = !entry.hasTotp()
    }

  /** Encrypts the password and the extra content */
  private fun encrypt() {
    with(binding) {
      val editName = filename.text.toString().trim()
      val editPass = password.text.toString()
      val editExtra = extraContent.text.toString()

      if (editName.isEmpty()) {
        snackbar(message = resources.getString(R.string.file_toast_text))
        return@with
      } else if (editName.contains('/')) {
        snackbar(message = resources.getString(R.string.invalid_filename_text))
        return@with
      }

      if (editPass.isEmpty() && editExtra.isEmpty()) {
        snackbar(message = resources.getString(R.string.empty_toast_text))
        return@with
      }

      if (copy) {
        copyPasswordToClipboard(editPass)
      }

      // pass enters the key ID into `.gpg-id`.
      val repoRoot = PasswordRepository.getRepositoryDirectory()
      val gpgIdentifierFile =
        File(repoRoot, directory.text.toString()).findTillRoot(".gpg-id", repoRoot)
          ?: File(repoRoot, ".gpg-id").apply { createNewFile() }
      val gpgIdentifiers =
        gpgIdentifierFile
          .readLines()
          .filter { it.isNotBlank() }
          .map { line ->
            GpgIdentifier.fromString(line)
              ?: run {
                // The line being empty means this is most likely an empty `.gpg-id`
                // file we created. Skip the validation so we can make the user add a
                // real ID.
                if (line.isEmpty()) return@run
                if (line.removePrefix("0x").matches("[a-fA-F0-9]{8}".toRegex()).not()) {
                  snackbar(message = resources.getString(R.string.invalid_gpg_id))
                }
                return@with
              }
          }
          .filterIsInstance<GpgIdentifier>()
      if (gpgIdentifiers.isEmpty()) {
        error("Failed to parse identifiers from .gpg-id")
      }
      val content = "$editPass\n$editExtra"
      val path =
        when {
          // If we allowed the user to edit the relative path, we have to consider it here
          // instead
          // of fullPath.
          directoryInputLayout.isEnabled -> {
            val editRelativePath = directory.text.toString().trim()
            if (editRelativePath.isEmpty()) {
              snackbar(message = resources.getString(R.string.path_toast_text))
              return
            }
            val passwordDirectory = File("$repoPath/${editRelativePath.trim('/')}")
            if (!passwordDirectory.exists() && !passwordDirectory.mkdir()) {
              snackbar(message = "Failed to create directory ${editRelativePath.trim('/')}")
              return
            }

            "${passwordDirectory.path}/$editName.gpg"
          }
          else -> "$fullPath/$editName.gpg"
        }

      lifecycleScope.launch(Dispatchers.Main) {
        runCatching {
            val result =
              withContext(Dispatchers.IO) {
                val outputStream = ByteArrayOutputStream()
                repository.encrypt(content.byteInputStream(), outputStream)
                outputStream
              }
            val file = File(path)
            // If we're not editing, this file should not already exist!
            // Additionally, if we were editing and the incoming and outgoing
            // filenames differ, it means we renamed. Ensure that the target
            // doesn't already exist to prevent an accidental overwrite.
            if (
              (!editing || (editing && suggestedName != file.nameWithoutExtension)) && file.exists()
            ) {
              snackbar(message = getString(R.string.password_creation_duplicate_error))
              return@runCatching
            }

            if (!file.isInsideRepository()) {
              snackbar(message = getString(R.string.message_error_destination_outside_repo))
              return@runCatching
            }

            withContext(Dispatchers.IO) { file.writeBytes(result.toByteArray()) }

            // associate the new password name with the last name's timestamp in
            // history
            val preference = getSharedPreferences("recent_password_history", Context.MODE_PRIVATE)
            val oldFilePathHash = "$repoPath/${oldCategory?.trim('/')}/$oldFileName.gpg".base64()
            val timestamp = preference.getString(oldFilePathHash)
            if (timestamp != null) {
              preference.edit {
                remove(oldFilePathHash)
                putString(file.absolutePath.base64(), timestamp)
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
              val username = entry.username ?: directoryStructure.getUsernameFor(file)
              returnIntent.putExtra(RETURN_EXTRA_USERNAME, username)
            }

            if (
              directoryInputLayout.isVisible &&
                directoryInputLayout.isEnabled &&
                oldFileName != null
            ) {
              val oldFile = File("$repoPath/${oldCategory?.trim('/')}/$oldFileName.gpg")
              if (oldFile.path != file.path && !oldFile.delete()) {
                setResult(RESULT_CANCELED)
                MaterialAlertDialogBuilder(this@PasswordCreationActivity)
                  .setTitle(R.string.password_creation_file_fail_title)
                  .setMessage(
                    getString(R.string.password_creation_file_delete_fail_message, oldFileName)
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

  @Suppress("ReturnCount")
  private fun File.findTillRoot(fileName: String, rootPath: File): File? {
    val gpgFile = File(this, fileName)
    if (gpgFile.exists()) return gpgFile

    if (this.absolutePath == rootPath.absolutePath) {
      return null
    }

    val parent = parentFile
    return if (parent != null && parent.exists()) {
      parent.findTillRoot(fileName, rootPath)
    } else {
      null
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
    const val EXTRA_PASSWORD = "PASSWORD"
    const val EXTRA_EXTRA_CONTENT = "EXTRA_CONTENT"
    const val EXTRA_GENERATE_PASSWORD = "GENERATE_PASSWORD"
    const val EXTRA_EDITING = "EDITING"
  }
}
