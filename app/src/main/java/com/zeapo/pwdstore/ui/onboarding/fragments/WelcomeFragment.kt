/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.ui.onboarding.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.FragmentWelcomeBinding
import com.zeapo.pwdstore.utils.performTransactionWithBackStack
import com.zeapo.pwdstore.utils.viewBinding

@Suppress("unused")
class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    private val binding by viewBinding(FragmentWelcomeBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.letsGo.setOnClickListener { parentFragmentManager.performTransactionWithBackStack(CloneFragment.newInstance()) }
    }
}
