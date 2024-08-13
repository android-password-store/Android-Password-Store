/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.ui.proxy

import android.content.SharedPreferences
import android.net.InetAddresses
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.postDelayed
import androidx.core.widget.doOnTextChanged
import app.passwordstore.R
import app.passwordstore.databinding.ActivityProxySelectorBinding
import app.passwordstore.injection.prefs.ProxyPreferences
import app.passwordstore.util.extensions.getString
import app.passwordstore.util.extensions.viewBinding
import app.passwordstore.util.proxy.ProxyUtils
import app.passwordstore.util.settings.GitSettings
import app.passwordstore.util.settings.PreferenceKeys
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProxySelectorActivity : AppCompatActivity() {

  @Inject lateinit var gitSettings: GitSettings
  @ProxyPreferences @Inject lateinit var proxyPrefs: SharedPreferences
  @Inject lateinit var proxyUtils: ProxyUtils
  private val binding by viewBinding(ActivityProxySelectorBinding::inflate)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    with(binding) {
      proxyHost.setText(proxyPrefs.getString(PreferenceKeys.PROXY_HOST))
      proxyUser.setText(proxyPrefs.getString(PreferenceKeys.PROXY_USERNAME))
      proxyPrefs
        .getInt(PreferenceKeys.PROXY_PORT, -1)
        .takeIf { it != -1 }
        ?.let { proxyPort.setText("$it") }
      proxyPassword.setText(proxyPrefs.getString(PreferenceKeys.PROXY_PASSWORD))
      save.setOnClickListener { saveSettings() }
      proxyHost.doOnTextChanged { text, _, _, _ ->
        if (text != null) {
          proxyHost.error =
            if (isNumericAddress(text) || text.matches(WEB_ADDRESS_REGEX)) {
              null
            } else {
              getString(R.string.invalid_proxy_url)
            }
        }
      }
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      android.R.id.home -> {
        onBackPressedDispatcher.onBackPressed()
      }
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  private fun isNumericAddress(text: CharSequence): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      InetAddresses.isNumericAddress(text as String)
    } else {
      @Suppress("DEPRECATION") Patterns.IP_ADDRESS.matcher(text).matches()
    }
  }

  private fun saveSettings() {
    proxyPrefs.edit {
      binding.proxyHost.text
        ?.toString()
        ?.takeIf { it.isNotEmpty() }
        .let { gitSettings.proxyHost = it }
      binding.proxyUser.text
        ?.toString()
        ?.takeIf { it.isNotEmpty() }
        .let { gitSettings.proxyUsername = it }
      binding.proxyPort.text
        ?.toString()
        ?.takeIf { it.isNotEmpty() }
        ?.let { gitSettings.proxyPort = it.toInt() }
      binding.proxyPassword.text
        ?.toString()
        ?.takeIf { it.isNotEmpty() }
        .let { gitSettings.proxyPassword = it }
    }
    proxyUtils.setDefaultProxy()
    Handler(Looper.getMainLooper()).postDelayed(500) { finish() }
  }

  private companion object {
    private val WEB_ADDRESS_REGEX = Patterns.WEB_URL.toRegex()
  }
}
