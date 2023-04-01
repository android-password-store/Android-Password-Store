/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.ui.sshkeygen

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import app.passwordstore.R
import app.passwordstore.ssh.SSHKeyManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ShowSshKeyFragment : DialogFragment() {

  @Inject lateinit var sshKeyManager: SSHKeyManager
  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val activity = requireActivity()
    val publicKey = sshKeyManager.publicKey()
    return MaterialAlertDialogBuilder(requireActivity()).run {
      setMessage(getString(R.string.ssh_keygen_message, publicKey))
      setTitle(R.string.your_public_key)
      setNegativeButton(R.string.ssh_keygen_later) { _, _ ->
        (activity as? SshKeyGenActivity)?.finish()
      }
      setPositiveButton(R.string.ssh_keygen_share) { _, _ ->
        val sendIntent =
          Intent().apply {
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
