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
import kotlinx.android.synthetic.main.fragment_to_clone_or_not.clone_from_server_button
import kotlinx.android.synthetic.main.fragment_to_clone_or_not.local_directory_button
import kotlinx.android.synthetic.main.fragment_to_clone_or_not.settings_button

class ToCloneOrNot : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_to_clone_or_not, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings_button.setOnClickListener { startActivity(Intent(requireContext(), UserPreference::class.java)) }
        local_directory_button.setOnClickListener { (requireActivity() as PasswordStore).initRepository(PasswordStore.NEW_REPO_BUTTON) }
        clone_from_server_button.setOnClickListener { (requireActivity() as PasswordStore).initRepository(PasswordStore.CLONE_REPO_BUTTON) }
    }
}
