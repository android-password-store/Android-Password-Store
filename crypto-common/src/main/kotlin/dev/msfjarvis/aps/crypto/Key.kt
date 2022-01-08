/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

/**
 * A simple value class wrapping over a [ByteArray] that can be used as a key type for cryptographic
 * purposes. The public/private distinction is elided specifically to defer that decision to
 * implementations of [KeyManager]. Similarly, identification of the key's identities is also
 * deferred to [KeyManager] to ensure maximum flexibility.
 */
@JvmInline public value class Key(public val contents: ByteArray)
