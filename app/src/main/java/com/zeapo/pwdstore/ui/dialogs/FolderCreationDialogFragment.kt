/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.zeapo.pwdstore.PasswordStore
import com.zeapo.pwdstore.R
import java.io.File

class FolderCreationDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder = MaterialAlertDialogBuilder(requireContext())
        alertDialogBuilder.setTitle(R.string.title_create_folder)
        alertDialogBuilder.setView(R.layout.folder_creation_dialog_fragment)
        alertDialogBuilder.setPositiveButton(getString(R.string.button_create)) { _, _ ->
            createDirectory(requireArguments().getString(CURRENT_DIR_EXTRA)!!)
        }
        alertDialogBuilder.setNegativeButton(getString(android.R.string.cancel)) { _, _ ->
            dismiss()
        }
        val dialog = alertDialogBuilder.create()
        dialog.setOnShowListener {
            // https://stackoverflow.com/a/13056259/297261
            dialog.findViewById<TextInputEditText>(R.id.folder_name_text)!!.apply {
                setOnFocusChangeListener { v, _ ->
                    v.post {
                        val imm = activity!!.getSystemService(InputMethodManager::class.java)
                        imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                requestFocus()
            }
        }
        return dialog
    }

    private fun createDirectory(currentDir: String) {
        val dialog = requireDialog()
        val materialTextView = dialog.findViewById<TextInputEditText>(R.id.folder_name_text)
        val folderName = materialTextView.text.toString()
        File("$currentDir/$folderName").mkdir()
        (requireActivity() as PasswordStore).refreshPasswordList()
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
