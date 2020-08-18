/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.sshkeygen

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.git.sshj.SshKey

class ShowSshKeyFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val publicKey = SshKey.sshPublicKey
        return MaterialAlertDialogBuilder(requireActivity()).run {
            setView(view)
            setMessage(getString(R.string.ssh_keygen_message, publicKey))
            setTitle(R.string.your_public_key)
            setNegativeButton(R.string.ssh_keygen_later) { _, _ ->
                (activity as? SshKeyGenActivity)?.finish()
            }
            setPositiveButton(R.string.ssh_keygen_share) { _, _ ->
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, publicKey)
                }
                startActivity(Intent.createChooser(sendIntent, null))
                (activity as? SshKeyGenActivity)?.finish()
            }
            create()
        }
    }
}
