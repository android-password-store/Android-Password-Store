/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.onboarding.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.ui.settings.UserPreference
import dev.msfjarvis.aps.databinding.FragmentWelcomeBinding
import dev.msfjarvis.aps.util.extensions.performTransactionWithBackStack
import dev.msfjarvis.aps.util.extensions.viewBinding

@Keep
@Suppress("unused")
class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    private val binding by viewBinding(FragmentWelcomeBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.letsGo.setOnClickListener { parentFragmentManager.performTransactionWithBackStack(CloneFragment.newInstance()) }
        binding.settingsButton.setOnClickListener { startActivity(Intent(requireContext(), UserPreference::class.java)) }
    }
}
