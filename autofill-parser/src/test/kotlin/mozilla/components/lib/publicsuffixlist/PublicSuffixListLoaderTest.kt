/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package mozilla.components.lib.publicsuffixlist

import kotlin.test.Test

class PublicSuffixListLoaderTest {
  @Test
  fun testLoadingBundledPublicSuffixList() {
    requireNotNull(javaClass.classLoader) { "Null classloader????" }
      .getResourceAsStream("publicsuffixes")
      .buffered()
      .use { stream -> PublicSuffixListLoader.load(stream) }
  }
}
