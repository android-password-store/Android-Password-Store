/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zeapo.pwdstore.databinding.FragmentToCloneOrNotBinding
import com.zeapo.pwdstore.utils.viewBinding

class ToCloneOrNot : Fragment() {

    private val binding by viewBinding(FragmentToCloneOrNotBinding::bind)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? = binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.settingsButton.setOnClickListener { startActivity(Intent(requireContext(), UserPreference::class.java)) }
        binding.localDirectoryButton.setOnClickListener { (requireActivity() as PasswordStore).initRepository(PasswordStore.NEW_REPO_BUTTON) }
        binding.cloneFromServerButton.setOnClickListener { (requireActivity() as PasswordStore).initRepository(PasswordStore.CLONE_REPO_BUTTON) }
    }
}
