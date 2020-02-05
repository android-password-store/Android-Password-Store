/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.zeapo.pwdstore.PasswordStore
import com.zeapo.pwdstore.R
import java.io.File

class FolderCreationDialogFragment : DialogFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextInputEditText>(R.id.folder_name_text).requestFocus()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder = MaterialAlertDialogBuilder(requireContext())
        alertDialogBuilder.setTitle(R.string.title_create_folder)
        alertDialogBuilder.setView(R.layout.folder_creation_dialog_fragment)
        alertDialogBuilder.setPositiveButton(getString(R.string.button_create)) { _, _ ->
            createDirectory(requireArguments().getString(CURRENT_DIR_EXTRA)!!)
        }
        return alertDialogBuilder.create()
    }

    private fun createDirectory(currentDir: String) {
        val dialog = requireDialog()
        val materialTextView = dialog.findViewById<TextInputEditText>(R.id.folder_name_text)
        val folderName = materialTextView.text.toString()
        File("$currentDir/$folderName").mkdir()
        (requireActivity() as PasswordStore).updateListAdapter()
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
