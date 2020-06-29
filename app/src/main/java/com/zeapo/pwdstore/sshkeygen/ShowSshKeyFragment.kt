/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.sshkeygen

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.clipboard
import java.io.File

class ShowSshKeyFragment : DialogFragment() {

    private lateinit var builder: MaterialAlertDialogBuilder
    private lateinit var publicKey: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        builder = MaterialAlertDialogBuilder(requireActivity())
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val view = activity.layoutInflater.inflate(R.layout.fragment_show_ssh_key, null)
        publicKey = view.findViewById(R.id.public_key)
        readKeyFromFile()
        createMaterialDialog(view)
        val ad = builder.create()
        ad.setOnShowListener {
            val b = ad.getButton(AlertDialog.BUTTON_POSITIVE)
            b.setOnClickListener {
                val clipboard = activity.clipboard ?: return@setOnClickListener
                val clip = ClipData.newPlainText("public key", publicKey.text.toString())
                clipboard.setPrimaryClip(clip)
            }
        }
        return ad
    }

    private fun createMaterialDialog(view: View) {
        builder.setView(view)
        builder.setTitle(getString(R.string.your_public_key))
        builder.setNegativeButton(R.string.dialog_ok) { _, _ -> requireActivity().finish() }
        builder.setPositiveButton(R.string.ssh_keygen_copy, null)
    }

    private fun readKeyFromFile() {
        val file = File(requireActivity().filesDir.toString() + "/.ssh_key.pub")
        try {
            publicKey.text = file.readText()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
