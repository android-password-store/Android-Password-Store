/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.msfjarvis.aps.databinding.FragmentRemoteSettingsBinding
import dev.msfjarvis.aps.databinding.FragmentRepoLocationBinding
import dev.msfjarvis.aps.utils.performTransactionWithBackStack

class RemoteSettingsFragment : Fragment() {

  private lateinit var binding: FragmentRemoteSettingsBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    super.onCreateView(inflater, container, savedInstanceState)
    binding = FragmentRemoteSettingsBinding.inflate(inflater)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.btnPositive.setOnClickListener {
      /*
      OpenKeychain's going to change this soon so no point in using this right now.
      parentFragmentManager.performTransactionWithBackStack(PGPProviderFragment.newInstance())
       */
    }
  }

  companion object {
    fun newInstance(): RemoteSettingsFragment = RemoteSettingsFragment()
  }
}
