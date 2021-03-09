/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.crypto

import kotlin.test.Ignore
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test

class GpgIdentifierTest {

    @Test
    fun `parses hexadecimal key id without leading 0x`() {
        val identifier = GpgIdentifier.fromString("79E8208280490C77")
        assertNotNull(identifier)
        assertTrue { identifier is GpgIdentifier.KeyId }
    }

    @Test
    fun `parses hexadecimal key id`() {
        val identifier = GpgIdentifier.fromString("0x79E8208280490C77")
        assertNotNull(identifier)
        assertTrue { identifier is GpgIdentifier.KeyId }
    }

    @Test
    fun `parses email as user id`() {
        val identifier = GpgIdentifier.fromString("aps@msfjarvis.dev")
        assertNotNull(identifier)
        assertTrue { identifier is GpgIdentifier.UserId }
    }

    @Test
    @Ignore("OpenKeychain can't yet handle these so we don't either")
    fun `parses non-email user id`() {
        val identifier = GpgIdentifier.fromString("john.doe")
        assertNotNull(identifier)
        assertTrue { identifier is GpgIdentifier.UserId }
    }
}
