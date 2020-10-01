/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.ui.onboarding.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.crypto.GetKeyIdsActivity
import com.zeapo.pwdstore.databinding.FragmentKeySelectionBinding
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.commitChange
import com.zeapo.pwdstore.utils.finish
import com.zeapo.pwdstore.utils.sharedPrefs
import com.zeapo.pwdstore.utils.viewBinding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.msfjarvis.openpgpktx.util.OpenPgpApi

class KeySelectionFragment : Fragment(R.layout.fragment_key_selection) {

    private val settings by lazy { requireActivity().applicationContext.sharedPrefs }
    private val binding by viewBinding(FragmentKeySelectionBinding::bind)

    private val gpgKeySelectAction = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            result.data?.getStringArrayExtra(OpenPgpApi.EXTRA_KEY_IDS)?.let { keyIds ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        val gpgIdentifierFile = File(PasswordRepository.getRepositoryDirectory(), ".gpg-id")
                        gpgIdentifierFile.writeText(keyIds.joinToString("\n"))
                    }
                    settings.edit { putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, true) }
                    requireActivity().commitChange(getString(
                        R.string.git_commit_gpg_id,
                        getString(R.string.app_name)
                    ))
                }
            }
        } else {
            throw IllegalStateException("Failed to initialize repository state.")
        }
        finish()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.selectKey.setOnClickListener { gpgKeySelectAction.launch(Intent(requireContext(), GetKeyIdsActivity::class.java)) }
    }

    companion object {

        fun newInstance() = KeySelectionFragment()
    }
}
