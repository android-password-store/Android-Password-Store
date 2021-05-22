/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.ui.proxy

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.postDelayed
import androidx.core.widget.doOnTextChanged
import dagger.hilt.android.AndroidEntryPoint
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.databinding.ActivityProxySelectorBinding
import dev.msfjarvis.aps.util.extensions.getEncryptedProxyPrefs
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.viewBinding
import dev.msfjarvis.aps.util.proxy.ProxyUtils
import dev.msfjarvis.aps.util.settings.GitSettings
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import javax.inject.Inject

private val IP_ADDRESS_REGEX = Patterns.IP_ADDRESS.toRegex()
private val WEB_ADDRESS_REGEX = Patterns.WEB_URL.toRegex()

@AndroidEntryPoint
class ProxySelectorActivity : AppCompatActivity() {

  @Inject lateinit var gitSettings: GitSettings
  @Inject lateinit var proxyUtils: ProxyUtils

  private val binding by viewBinding(ActivityProxySelectorBinding::inflate)
  private val proxyPrefs by lazy(LazyThreadSafetyMode.NONE) {
    applicationContext.getEncryptedProxyPrefs()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)
    with(binding) {
      proxyHost.setText(proxyPrefs.getString(PreferenceKeys.PROXY_HOST))
      proxyUser.setText(proxyPrefs.getString(PreferenceKeys.PROXY_USERNAME))
      proxyPrefs.getInt(PreferenceKeys.PROXY_PORT, -1).takeIf { it != -1 }?.let {
        proxyPort.setText("$it")
      }
      proxyPassword.setText(proxyPrefs.getString(PreferenceKeys.PROXY_PASSWORD))
      save.setOnClickListener { saveSettings() }
      proxyHost.doOnTextChanged { text, _, _, _ ->
        if (text != null) {
          proxyHost.error =
            if (text.matches(IP_ADDRESS_REGEX) || text.matches(WEB_ADDRESS_REGEX)) {
              null
            } else {
              getString(R.string.invalid_proxy_url)
            }
        }
      }
    }
  }

  private fun saveSettings() {
    proxyPrefs.edit {
      binding.proxyHost.text?.toString()?.takeIf { it.isNotEmpty() }.let {
        gitSettings.proxyHost = it
      }
      binding.proxyUser.text?.toString()?.takeIf { it.isNotEmpty() }.let {
        gitSettings.proxyUsername = it
      }
      binding.proxyPort.text?.toString()?.takeIf { it.isNotEmpty() }?.let {
        gitSettings.proxyPort = it.toInt()
      }
      binding.proxyPassword.text?.toString()?.takeIf { it.isNotEmpty() }.let {
        gitSettings.proxyPassword = it
      }
    }
    proxyUtils.setDefaultProxy()
    Handler(Looper.getMainLooper()).postDelayed(500) { finish() }
  }
}
