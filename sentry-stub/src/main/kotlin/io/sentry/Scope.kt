/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("Unused", "UNUSED_PARAMETER")

package io.sentry

import io.sentry.protocol.User

public class Scope {
  public var user: User? = null

  public fun setTag(tag: String, value: String) {}
}
