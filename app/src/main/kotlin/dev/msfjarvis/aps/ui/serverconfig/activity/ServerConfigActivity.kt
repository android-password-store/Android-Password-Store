/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.ui.serverconfig.activity

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import dev.msfjarvis.aps.databinding.ActivityServerConfigBinding
import dev.msfjarvis.aps.ui.EdgeToEdge

class ServerConfigActivity : AppCompatActivity() {

  private lateinit var binding: ActivityServerConfigBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityServerConfigBinding.inflate(layoutInflater)
    EdgeToEdge.apply {
      setUpRoot(binding.root as ViewGroup)
      setUpAppBar(binding.serverConfigAppbar)
    }
    setContentView(binding.root)
    setSupportActionBar(binding.serverConfigToolbar)
  }
}
