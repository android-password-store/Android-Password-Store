/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.data.passfile

import kotlin.time.Duration

/** Holder for a TOTP secret and the duration for which it is valid. */
public data class Totp(public val value: String, public val remainingTime: Duration)
