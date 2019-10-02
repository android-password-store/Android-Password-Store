/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0
 */
package com.zeapo.pwdstore.utils

import android.content.Context
import android.util.TypedValue

fun String.splitLines(): Array<String> {
    return split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
}

fun Context.resolveAttribute(attr: Int): Int {
    val typedValue = TypedValue()
    this.theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}
