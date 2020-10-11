/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.utils

import com.zeapo.pwdstore.git.config.GitSettings
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Utility class for [Proxy] handling.
 */
object ProxyUtils {

    private const val HTTP_PROXY_USER_PROPERTY = "http.proxyUser"
    private const val HTTP_PROXY_PASSWORD_PROPERTY = "http.proxyPassword"

    /**
     * Set the default [Proxy] and [Authenticator] for the app based on user provided settings.
     */
    fun setDefaultProxy() {
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): MutableList<Proxy> {
                val host = GitSettings.proxyHost
                val port = GitSettings.proxyPort
                return if (host == null || port == -1) {
                    mutableListOf(Proxy.NO_PROXY)
                } else {
                    mutableListOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)))
                }
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                if (uri == null || sa == null || ioe == null) {
                    throw IllegalArgumentException("Arguments can't be null.")
                }
            }
        })
        val user = GitSettings.proxyUsername ?: ""
        val password = GitSettings.proxyPassword ?: ""
        if (user.isEmpty() || password.isEmpty()) {
            System.clearProperty(HTTP_PROXY_USER_PROPERTY)
            System.clearProperty(HTTP_PROXY_PASSWORD_PROPERTY)
        } else {
            System.setProperty(HTTP_PROXY_USER_PROPERTY, user)
            System.setProperty(HTTP_PROXY_PASSWORD_PROPERTY, password)
        }
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                return if (requestorType == RequestorType.PROXY) {
                    PasswordAuthentication(user, password.toCharArray())
                } else {
                    null
                }
            }
        })
    }
}
