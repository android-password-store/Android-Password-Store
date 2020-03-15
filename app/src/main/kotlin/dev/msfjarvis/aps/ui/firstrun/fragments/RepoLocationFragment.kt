/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */
package dev.msfjarvis.aps.ui.firstrun.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.utils.SAFUtils
import dev.msfjarvis.aps.utils.SAFUtils.REQUEST_OPEN_DOCUMENT_TREE
import dev.msfjarvis.aps.databinding.FragmentRepoLocationBinding
import dev.msfjarvis.aps.di.activityViewModel
import dev.msfjarvis.aps.di.injector
import dev.msfjarvis.aps.ui.firstrun.viewmodels.FirstRunViewModel
import dev.msfjarvis.aps.ui.serverconfig.fragments.RemoteSettingsFragment
import dev.msfjarvis.aps.utils.performTransactionWithBackStack

class RepoLocationFragment : Fragment() {
  private var _binding: FragmentRepoLocationBinding? = null
  private val binding get() = _binding!!
  private val viewModel by activityViewModel { injector.firstRunViewModel }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    super.onCreateView(inflater, container, savedInstanceState)
    _binding = FragmentRepoLocationBinding.inflate(inflater)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.btnHidden.setOnClickListener {
      viewModel.setExternal(false)
      if (viewModel.isGitStore.value == true) {
        parentFragmentManager.performTransactionWithBackStack(RemoteSettingsFragment.newInstance())
      } else {
        parentFragmentManager.performTransactionWithBackStack(StoreNameFragment.newInstance())
      }
      /*
      OpenKeychain's going to change this soon so no point in using this right now.
      parentFragmentManager.performTransactionWithBackStack(PGPProviderFragment.newInstance())
       */
    }

    binding.btnSdcard.setOnClickListener {
      SAFUtils.openDirectory(this, null)
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_OPEN_DOCUMENT_TREE) {
      if (resultCode == Activity.RESULT_OK) {
        val directoryUri = data?.data
        if (directoryUri is Uri) {
          SAFUtils.makeUriPersistable(this.requireContext(), directoryUri)
          viewModel.setStoreUri(directoryUri)
          viewModel.setExternal(true)
          if (viewModel.isGitStore.value == true) {
            parentFragmentManager.performTransactionWithBackStack(RemoteSettingsFragment.newInstance())
          } else {
            parentFragmentManager.performTransactionWithBackStack(StoreNameFragment.newInstance())
          }
        } else {
          Snackbar.make(binding.root, getString(R.string.error_directory_uri), Snackbar.LENGTH_LONG).show()
        }
      } else if (resultCode == Activity.RESULT_CANCELED) {
        Snackbar.make(binding.root, getString(R.string.select_directory_passwords), Snackbar.LENGTH_LONG).show()
      }
    }
  }

  override fun onDestroyView() {
    _binding = null
    super.onDestroyView()
  }

  companion object {
    fun newInstance(): RepoLocationFragment = RepoLocationFragment()
  }
}
