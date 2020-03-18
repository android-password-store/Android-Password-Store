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
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.databinding.FragmentRepoLocationBinding
import dev.msfjarvis.aps.di.activityViewModel
import dev.msfjarvis.aps.di.injector
import dev.msfjarvis.aps.ui.serverconfig.fragments.RemoteSettingsFragment
import dev.msfjarvis.aps.utils.performTransactionWithBackStack
import dev.msfjarvis.aps.utils.SAFUtils.REQUEST_OPEN_DOCUMENT_TREE
import dev.msfjarvis.aps.utils.SAFUtils.makeUriPersistable
import dev.msfjarvis.aps.utils.SAFUtils.openDirectory

class RepoLocationFragment : Fragment() {

  private lateinit var binding: FragmentRepoLocationBinding
  private val viewModel by activityViewModel { injector.firstRunViewModel }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    super.onCreateView(inflater, container, savedInstanceState)
    binding = FragmentRepoLocationBinding.inflate(inflater)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.btnHidden.setOnClickListener {
      val documentTree = DocumentFile.fromFile(requireContext().filesDir)
      updateViewModel(documentTree.uri, persistUri = false, isExternal = false)
    }

    binding.btnSdcard.setOnClickListener {
      openDirectory(true, null)
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_OPEN_DOCUMENT_TREE && resultCode == Activity.RESULT_OK) {
      val directoryUri = data?.data
      if (directoryUri is Uri) {
        val directoryTree = requireNotNull(DocumentFile.fromTreeUri(requireContext(), directoryUri))
        if (directoryTree.isDirectory && directoryTree.listFiles().isEmpty()) {
          updateViewModel(directoryUri, persistUri = true, isExternal = true)
        } else {
          Snackbar.make(binding.root, getString(R.string.select_empty_directory), Snackbar.LENGTH_LONG).show()
        }
      } else {
        Snackbar.make(binding.root, getString(R.string.select_directory_passwords), Snackbar.LENGTH_LONG).show()
      }
    }
  }

  private fun updateViewModel(directoryUri: Uri, persistUri: Boolean, isExternal: Boolean) {
    if (persistUri) {
      makeUriPersistable(directoryUri)
    }
    viewModel.setStoreUri(directoryUri)
    viewModel.setExternal(isExternal)
    performFragmentTransaction()
  }

  private fun performFragmentTransaction() {
    if (viewModel.isGitStore.value == true) {
      parentFragmentManager.performTransactionWithBackStack(RemoteSettingsFragment.newInstance())
    } else {
      parentFragmentManager.performTransactionWithBackStack(StoreNameFragment.newInstance())
    }
  }

  companion object {
    fun newInstance(): RepoLocationFragment = RepoLocationFragment()
  }
}
