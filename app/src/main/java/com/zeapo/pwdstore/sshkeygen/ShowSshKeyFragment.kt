/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.sshkeygen

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import java.io.File
import java.nio.charset.StandardCharsets
import org.apache.commons.io.FileUtils

class ShowSshKeyFragment : DialogFragment() {

    private lateinit var activity: SshKeyGenActivity
    private lateinit var builder: MaterialAlertDialogBuilder
    private lateinit var publicKey: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = requireActivity() as SshKeyGenActivity
        builder = MaterialAlertDialogBuilder(activity)
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = activity.layoutInflater.inflate(R.layout.fragment_show_ssh_key, null)
        publicKey = view.findViewById(R.id.public_key)
        saveKeyToFile()
        createMaterialDialog(view)
        val ad = builder.create()
        ad.setOnShowListener {
            val b = ad.getButton(AlertDialog.BUTTON_NEUTRAL)
            b.setOnClickListener {
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("public key", publicKey.text.toString())
                clipboard.setPrimaryClip(clip)
            }
        }
        return ad
    }

    private fun createMaterialDialog(view: View) {
        builder.setView(view)
        builder.setTitle("Your public key")
        builder.setPositiveButton(resources.getString(R.string.dialog_ok)) {
            _: DialogInterface?, _: Int ->
            activity.finish()
        }
        builder.setNegativeButton(resources.getString(R.string.dialog_cancel)) {
            _: DialogInterface?, _: Int ->
        }
        builder.setNeutralButton(resources.getString(R.string.ssh_keygen_copy), null)
    }

    private fun saveKeyToFile() {
        val file = File(activity.filesDir.toString() + "/.ssh_key.pub")
        try {
            publicKey.text = FileUtils.readFileToString(file, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            println("Exception caught :(")
            e.printStackTrace()
        }
    }
}
