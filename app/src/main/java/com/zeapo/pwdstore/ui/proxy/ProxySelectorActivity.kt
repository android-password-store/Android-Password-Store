/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.ui.proxy

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.os.postDelayed
import androidx.core.widget.doOnTextChanged
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.databinding.ActivityProxySelectorBinding
import com.zeapo.pwdstore.util.settings.GitSettings
import com.zeapo.pwdstore.util.settings.PreferenceKeys
import com.zeapo.pwdstore.util.proxy.ProxyUtils
import com.zeapo.pwdstore.util.extensions.getEncryptedProxyPrefs
import com.zeapo.pwdstore.util.extensions.getString
import com.zeapo.pwdstore.util.extensions.viewBinding

private val IP_ADDRESS_REGEX = Patterns.IP_ADDRESS.toRegex()
private val WEB_ADDRESS_REGEX = Patterns.WEB_URL.toRegex()

class ProxySelectorActivity : AppCompatActivity() {

    private val binding by viewBinding(ActivityProxySelectorBinding::inflate)
    private val proxyPrefs by lazy(LazyThreadSafetyMode.NONE) { applicationContext.getEncryptedProxyPrefs() }

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
                    proxyHost.error = if (text.matches(IP_ADDRESS_REGEX) || text.matches(WEB_ADDRESS_REGEX)) {
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
                GitSettings.proxyHost = it
            }
            binding.proxyUser.text?.toString()?.takeIf { it.isNotEmpty() }.let {
                GitSettings.proxyUsername = it
            }
            binding.proxyPort.text?.toString()?.takeIf { it.isNotEmpty() }?.let {
                GitSettings.proxyPort = it.toInt()
            }
            binding.proxyPassword.text?.toString()?.takeIf { it.isNotEmpty() }.let {
                GitSettings.proxyPassword = it
            }
        }
        ProxyUtils.setDefaultProxy()
        Handler(Looper.getMainLooper()).postDelayed(500) { finish() }
    }
}
