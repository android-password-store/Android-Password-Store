/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */
package dev.msfjarvis.aps.ui.firstrun.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.msfjarvis.aps.databinding.FragmentCloneBinding
import dev.msfjarvis.aps.di.activityViewModel
import dev.msfjarvis.aps.di.injector
import dev.msfjarvis.aps.utils.performTransactionWithBackStack

class CloneFragment : Fragment() {

  private lateinit var binding: FragmentCloneBinding
  private val viewModel by activityViewModel { injector.firstRunViewModel }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    super.onCreateView(inflater, container, savedInstanceState)
    binding = FragmentCloneBinding.inflate(inflater)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.btnCloneRemote.setOnClickListener {
      viewModel.setGitStore(true)
      parentFragmentManager.performTransactionWithBackStack(RepoLocationFragment.newInstance())
    }
    binding.btnCreateLocal.setOnClickListener {
      viewModel.setGitStore(false)
      parentFragmentManager.performTransactionWithBackStack(RepoLocationFragment.newInstance())
    }
  }

  companion object {
    fun newInstance(): CloneFragment = CloneFragment()
  }
}
