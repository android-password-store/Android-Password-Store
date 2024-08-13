/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import app.passwordstore.R
import app.passwordstore.ui.passwords.PasswordStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File

class FolderCreationDialogFragment : DialogFragment() {

  private lateinit var newFolder: File

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
    // TODO(msfjarvis): Restore this functionality
    /*
        if (dialog.findViewById<MaterialCheckBox>(R.id.set_gpg_key).isChecked) {
          keySelectAction.launch(Intent(requireContext(), GetKeyIdsActivity::class.java))
          return
        } else {
          dismiss()
        }
    */
    dismiss()
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
