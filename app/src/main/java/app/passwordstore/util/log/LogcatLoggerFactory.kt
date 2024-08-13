/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.log

import java.util.concurrent.ConcurrentHashMap
import org.slf4j.ILoggerFactory
import org.slf4j.Logger

/**
 * [ILoggerFactory] implementation that passes out instances of [LogcatLogger], maintaining an
 * internal cache of [Logger]s to avoid duplicate initialization.
 */
class LogcatLoggerFactory : ILoggerFactory {

  private val loggers = ConcurrentHashMap<String, Logger>()

  override fun getLogger(name: String): Logger {
    return loggers.getOrPut(name) { LogcatLogger(name) }
  }
}
