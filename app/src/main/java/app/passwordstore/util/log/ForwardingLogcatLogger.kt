package app.passwordstore.util.log

import com.pandulapeter.beagle.log.BeagleLogger
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogPriority.DEBUG
import logcat.LogcatLogger

/**
 * Wrapper around [AndroidLogcatLogger] that ensures all logged messages are also forwarded to
 * [BeagleLogger].
 */
class ForwardingLogcatLogger(minPriority: LogPriority = DEBUG) : LogcatLogger {
  private val androidLogger = AndroidLogcatLogger(minPriority)

  override fun isLoggable(priority: LogPriority): Boolean {
    return androidLogger.isLoggable(priority)
  }

  override fun log(priority: LogPriority, tag: String, message: String) {
    androidLogger.log(priority, tag, message)
    BeagleLogger.log(message = "[$tag]: $message", label = "Logcat", isPersisted = true)
  }
}
