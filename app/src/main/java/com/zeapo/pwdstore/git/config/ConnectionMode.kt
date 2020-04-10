/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config

sealed class ConnectionMode {
    object Ssh : ConnectionMode() {
        override fun toString() = "ssh-key"
    }
    object Username : ConnectionMode() {
        override fun toString() = "username/password"
    }
    object OpenKeychain : ConnectionMode() {
        override fun toString() = "OpenKeychain"
    }

    companion object {
        fun fromString(type: String?): ConnectionMode = when (type) {
            "ssh-key", null -> Ssh
            "username/password" -> Username
            "OpenKeychain" -> OpenKeychain
            else -> throw IllegalArgumentException("$type is not a valid ConnectionMode")
        }
    }
}
