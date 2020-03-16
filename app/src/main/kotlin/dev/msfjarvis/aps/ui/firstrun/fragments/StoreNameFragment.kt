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
import com.google.android.material.snackbar.Snackbar
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.databinding.FragmentStoreNameBinding
import dev.msfjarvis.aps.di.activityViewModel
import dev.msfjarvis.aps.di.injector

class StoreNameFragment : Fragment() {

  private lateinit var binding: FragmentStoreNameBinding
  private val viewModel by activityViewModel { injector.firstRunViewModel }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    super.onCreateView(inflater, container, savedInstanceState)
    binding = FragmentStoreNameBinding.inflate(inflater)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.btnLetsGo.setOnClickListener {
      if (binding.tietStoreName.text.isNullOrEmpty()) {
        Snackbar.make(binding.root, getString(R.string.err_enter_store_name), Snackbar.LENGTH_LONG).show()
      } else {
        viewModel.setName(binding.tietStoreName.text.toString())
        viewModel.setInitialized(true)
        viewModel.addPasswordStore()
      }
    }
  }

  companion object {
    fun newInstance(): StoreNameFragment = StoreNameFragment()
  }
}
