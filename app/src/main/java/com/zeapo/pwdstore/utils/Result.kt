/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.utils

/**
 * Emulates the Rust Result enum but without returning a value in the [Ok] case.
 * https://doc.rust-lang.org/std/result/enum.Result.html
 */
sealed class Result {

    object Ok : Result()
    data class Err(val err: Exception) : Result()

    fun isOk() = this is Ok
}
