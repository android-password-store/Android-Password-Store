/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package com.zeapo.pwdstore.ui.onboarding.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.zeapo.pwdstore.databinding.FragmentCloneBinding
import com.zeapo.pwdstore.git.BaseGitActivity
import com.zeapo.pwdstore.git.GitServerConfigActivity
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.performTransactionWithBackStack
import com.zeapo.pwdstore.utils.sharedPrefs

class CloneFragment : Fragment() {

    private lateinit var binding: FragmentCloneBinding
    private val settings by lazy { requireActivity().applicationContext.sharedPrefs }

    private val cloneAction = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            settings.edit { putBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, true) }
            requireActivity().finish()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentCloneBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnCloneRemote.setOnClickListener {
            cloneToHiddenDir()
        }
        binding.btnCreateLocal.setOnClickListener {
            parentFragmentManager.performTransactionWithBackStack(RepoLocationFragment.newInstance())
        }
    }

    /**
     * Clones a remote Git repository to the app's private directory
     */
    private fun cloneToHiddenDir() {
        settings.edit { putBoolean(PreferenceKeys.GIT_EXTERNAL, false) }
        cloneAction.launch(Intent(requireActivity(), GitServerConfigActivity::class.java).apply {
            putExtra(BaseGitActivity.REQUEST_ARG_OP, BaseGitActivity.REQUEST_CLONE)
        })
    }

    companion object {

        fun newInstance(): CloneFragment = CloneFragment()
    }
}
