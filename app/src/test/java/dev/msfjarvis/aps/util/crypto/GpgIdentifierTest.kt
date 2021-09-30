/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.crypto

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    val identifier = GpgIdentifier.fromString("john.doe@example.org")
    assertNotNull(identifier)
    assertTrue { identifier is GpgIdentifier.UserId }
  }

  @Test
  fun `parses user@host without TLD`() {
    val identifier = GpgIdentifier.fromString("john.doe@example")
    assertNotNull(identifier)
    assertTrue { identifier is GpgIdentifier.UserId }
  }
}
