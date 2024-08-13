/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.log

import logcat.LogPriority
import logcat.asLog
import logcat.logcat

/** An implementation of [AbstractLogger] that forwards logging calls to [logcat]. */
class LogcatLogger(name: String) : AbstractLogger(name) {

  override fun isTraceEnabled() = true

  override fun isDebugEnabled() = true

  override fun isInfoEnabled() = true

  override fun isWarnEnabled() = true

  override fun isErrorEnabled() = true

  // Replace slf4j's "{}" format string style with standard Java's "%s".
  // The supposedly redundant escape on the } is not redundant.
  @Suppress("RegExpRedundantEscape")
  private fun String.fix() = replace("""(?!<\\)\{\}""".toRegex(), "%s")

  override fun t(message: String, t: Throwable?, vararg args: Any?) {
    logcat(name, LogPriority.VERBOSE) { message.fix().format(*args) + (t?.asLog() ?: "") }
  }

  override fun d(message: String, t: Throwable?, vararg args: Any?) {
    logcat(name) { message.fix().format(*args) + (t?.asLog() ?: "") }
  }

  override fun i(message: String, t: Throwable?, vararg args: Any?) {
    logcat(name, LogPriority.INFO) { message.fix().format(*args) + (t?.asLog() ?: "") }
  }

  override fun w(message: String, t: Throwable?, vararg args: Any?) {
    logcat(name, LogPriority.WARN) { message.fix().format(*args) + (t?.asLog() ?: "") }
  }

  override fun e(message: String, t: Throwable?, vararg args: Any?) {
    logcat(name, LogPriority.ERROR) { message.fix().format(*args) + (t?.asLog() ?: "") }
  }
}
