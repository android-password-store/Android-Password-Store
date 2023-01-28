@file:Suppress("Unused")

package org.slf4j.impl

import org.slf4j.helpers.BasicMDCAdapter
import org.slf4j.spi.MDCAdapter

class StaticMDCBinder {

  fun getMDCA(): MDCAdapter {
    return BasicMDCAdapter()
  }

  fun getMDCAdapterClassStr(): String? {
    return BasicMDCAdapter::class.java.name
  }

  companion object {
    @JvmStatic val singleton = StaticMDCBinder()
  }
}
