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
import dev.msfjarvis.aps.databinding.FragmentPgpProviderBinding

@Suppress("Unused")
class PGPProviderFragment : Fragment() {

  private lateinit var binding: FragmentPgpProviderBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    super.onCreateView(inflater, container, savedInstanceState)
    binding = FragmentPgpProviderBinding.inflate(inflater)
    return binding.root
  }

  companion object {

    fun newInstance(): PGPProviderFragment = PGPProviderFragment()
  }
}
