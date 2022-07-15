/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.onboarding.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import app.passwordstore.R
import app.passwordstore.databinding.FragmentKeySelectionBinding
import app.passwordstore.util.extensions.viewBinding

class KeySelectionFragment : Fragment(R.layout.fragment_key_selection) {

  private val binding by viewBinding(FragmentKeySelectionBinding::bind)
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding.selectKey.setOnClickListener {
      // TODO(msfjarvis): Restore this functionality
      // gpgKeySelectAction.launch(Intent(requireContext(), GetKeyIdsActivity::class.java))
    }
  }

  companion object {

    fun newInstance() = KeySelectionFragment()
  }
}
