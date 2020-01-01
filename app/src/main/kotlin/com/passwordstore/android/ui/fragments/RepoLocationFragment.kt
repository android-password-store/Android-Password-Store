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
import com.passwordstore.android.databinding.FragmentRepoLocationBinding
import com.passwordstore.android.utils.performTransactionWithBackStack

class RepoLocationFragment : Fragment() {

    lateinit var binding: FragmentRepoLocationBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentRepoLocationBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnHidden.setOnClickListener {
            parentFragmentManager.performTransactionWithBackStack(PGPProviderFragment.newInstance())
        }
    }

    companion object {
        fun newInstance(): RepoLocationFragment = RepoLocationFragment()
    }
}
