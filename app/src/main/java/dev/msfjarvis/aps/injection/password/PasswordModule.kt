/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.injection.password

import dagger.assisted.AssistedFactory
import dev.msfjarvis.aps.data.passfile.PasswordEntry
import kotlinx.coroutines.CoroutineScope

@AssistedFactory
interface PasswordEntryFactory {
  fun create(scope: CoroutineScope, bytes: ByteArray): PasswordEntry
}
