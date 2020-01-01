/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.passwordstore.android.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.passwordstore.android.databinding.FragmentWelcomeBinding
import com.passwordstore.android.utils.performTransactionWithBackStack

class WelcomeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val binding = FragmentWelcomeBinding.inflate(layoutInflater)
        binding.btnGo.setOnClickListener { parentFragmentManager.performTransactionWithBackStack(CloneFragment.newInstance()) }
        return binding.root
    }
}
