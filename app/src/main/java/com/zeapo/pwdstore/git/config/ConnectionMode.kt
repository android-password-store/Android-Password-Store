/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config

enum class ConnectionMode(val pref: String) {
    SshKey("ssh-key"),
    Password("username/password"),
    OpenKeychain("OpenKeychain"),
    None("None"),
    ;

    companion object {

        private val map = values().associateBy(ConnectionMode::pref)
        fun fromString(type: String?): ConnectionMode {
            return map[type ?: return SshKey]
                ?: throw IllegalArgumentException("$type is not a valid ConnectionMode")
        }
    }
}
