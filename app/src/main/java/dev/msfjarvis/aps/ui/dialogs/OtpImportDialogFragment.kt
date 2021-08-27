/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.dialogs

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.msfjarvis.aps.databinding.FragmentManualOtpEntryBinding
import dev.msfjarvis.aps.ui.crypto.PasswordCreationActivity

class OtpImportDialogFragment : DialogFragment() {

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = MaterialAlertDialogBuilder(requireContext())
    val binding = FragmentManualOtpEntryBinding.inflate(layoutInflater)
    builder.setView(binding.root)
    builder.setPositiveButton(android.R.string.ok) { _, _ ->
      setFragmentResult(
        PasswordCreationActivity.OTP_RESULT_REQUEST_KEY,
        bundleOf(PasswordCreationActivity.RESULT to getTOTPUri(binding))
      )
    }
    val dialog = builder.create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    return dialog
  }

  private fun getTOTPUri(binding: FragmentManualOtpEntryBinding): String {
    val secret = binding.secret.text.toString()
    val account = binding.account.text.toString()
    if (secret.isBlank()) return ""
    val builder = Uri.Builder()
    builder.scheme("otpauth")
    builder.authority("totp")
    builder.appendQueryParameter("secret", secret)
    if (account.isNotBlank()) builder.appendQueryParameter("issuer", account)
    return builder.build().toString()
  }
}
