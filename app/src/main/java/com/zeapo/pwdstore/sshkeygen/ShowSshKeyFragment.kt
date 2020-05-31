/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.sshkeygen

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.clipboardManager
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.charset.StandardCharsets

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
        return builder.run {
            setView(view)
            setTitle(R.string.your_public_key)
            setPositiveButton(R.string.ssh_keygen_copy) { _, _ ->
                val clip = ClipData.newPlainText("public key", publicKey.text.toString())
                context.clipboardManager?.setPrimaryClip(clip)
                Toast.makeText(context, R.string.ssh_keygen_copied_key, Toast.LENGTH_SHORT).show()
            }
            setNegativeButton(R.string.dialog_cancel, null)
            create()
        }
    }

    private fun readKeyFromFile() {
        val file = File(requireActivity().filesDir.toString() + "/.ssh_key.pub")
        try {
            publicKey.text = FileUtils.readFileToString(file, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
