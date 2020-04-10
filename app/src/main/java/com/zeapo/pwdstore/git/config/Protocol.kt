/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config

sealed class Protocol {
    object Ssh : Protocol() {
        override fun toString() = "ssh://"
    }
    object Https : Protocol() {
        override fun toString() = "https://"
    }

    companion object {
        fun fromString(type: String?): Protocol = when (type) {
            "ssh://", null -> Ssh
            "https://" -> Https
            else -> throw IllegalArgumentException("$type is not a valid Protocol")
        }
    }
}
