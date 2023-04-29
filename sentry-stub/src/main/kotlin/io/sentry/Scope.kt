@file:Suppress("Unused", "UNUSED_PARAMETER")

package io.sentry

import io.sentry.protocol.User

public class Scope {
  public var user: User? = null

  public fun setTag(tag: String, value: String) {}
}
