@file:Suppress("Unused", "UNUSED_PARAMETER")

package io.sentry.protocol

public data class User(
  public var email: String? = null,
  public var id: String? = null,
  public var username: String? = null,
  public var ipAddress: String? = null,
  public var data: Map<String?, String>? = null,
  public var unknown: Map<String?, String>? = null,
)
