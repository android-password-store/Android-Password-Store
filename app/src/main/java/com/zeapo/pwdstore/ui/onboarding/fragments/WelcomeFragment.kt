/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.onboarding.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.zeapo.pwdstore.databinding.FragmentWelcomeBinding
import com.zeapo.pwdstore.utils.performTransactionWithBackStack

class WelcomeFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentWelcomeBinding.inflate(layoutInflater)
        binding.letsGo.setOnClickListener { parentFragmentManager.performTransactionWithBackStack(CloneFragment.newInstance()) }
        return binding.root
    }
}
