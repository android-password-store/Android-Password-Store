/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.dialogs

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.data.repo.PasswordRepository
import dev.msfjarvis.aps.ui.crypto.BasePgpActivity
import dev.msfjarvis.aps.ui.crypto.GetKeyIdsActivity
import dev.msfjarvis.aps.ui.passwords.PasswordStore
import dev.msfjarvis.aps.util.extensions.commitChange
import java.io.File
import kotlinx.coroutines.launch
import me.msfjarvis.openpgpktx.util.OpenPgpApi

class FolderCreationDialogFragment : DialogFragment() {

  private lateinit var newFolder: File

  private val keySelectAction =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == AppCompatActivity.RESULT_OK) {
        result.data?.getStringArrayExtra(OpenPgpApi.EXTRA_KEY_IDS)?.let { keyIds ->
          val gpgIdentifierFile = File(newFolder, ".gpg-id")
          gpgIdentifierFile.writeText(keyIds.joinToString("\n"))
          val repo = PasswordRepository.getRepository(null)
          if (repo != null) {
            lifecycleScope.launch {
              val repoPath = PasswordRepository.getRepositoryDirectory().absolutePath
              requireActivity()
                .commitChange(
                  getString(
                    R.string.git_commit_gpg_id,
                    BasePgpActivity.getLongName(
                      gpgIdentifierFile.parentFile!!.absolutePath,
                      repoPath,
                      gpgIdentifierFile.name
                    )
                  ),
                )
              dismiss()
            }
          }
        }
      }
    }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val alertDialogBuilder = MaterialAlertDialogBuilder(requireContext())
    alertDialogBuilder.setTitle(R.string.title_create_folder)
    alertDialogBuilder.setView(R.layout.folder_dialog_fragment)
    alertDialogBuilder.setPositiveButton(getString(R.string.button_create), null)
    alertDialogBuilder.setNegativeButton(getString(android.R.string.cancel)) { _, _ -> dismiss() }
    val dialog = alertDialogBuilder.create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        createDirectory(requireArguments().getString(CURRENT_DIR_EXTRA)!!)
      }
    }
    return dialog
  }

  private fun createDirectory(currentDir: String) {
    val dialog = requireDialog()
    val folderNameView = dialog.findViewById<TextInputEditText>(R.id.folder_name_text)
    val folderNameViewContainer = dialog.findViewById<TextInputLayout>(R.id.folder_name_container)
    newFolder = File("$currentDir/${folderNameView.text}")
    folderNameViewContainer.error =
      when {
        newFolder.isFile -> getString(R.string.folder_creation_err_file_exists)
        newFolder.isDirectory -> getString(R.string.folder_creation_err_folder_exists)
        else -> null
      }
    if (folderNameViewContainer.error != null) return
    newFolder.mkdirs()
    (requireActivity() as PasswordStore).refreshPasswordList(newFolder)
    if (dialog.findViewById<MaterialCheckBox>(R.id.set_gpg_key).isChecked) {
      keySelectAction.launch(Intent(requireContext(), GetKeyIdsActivity::class.java))
      return
    } else {
      dismiss()
    }
  }

  companion object {

    private const val CURRENT_DIR_EXTRA = "CURRENT_DIRECTORY"
    fun newInstance(startingDirectory: String): FolderCreationDialogFragment {
      val extras = bundleOf(CURRENT_DIR_EXTRA to startingDirectory)
      val fragment = FolderCreationDialogFragment()
      fragment.arguments = extras
      return fragment
    }
  }
}
