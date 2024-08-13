/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.log

import org.slf4j.Logger
import org.slf4j.Marker

/**
 * A bridge class to ease the implementation of the default SLF4J [Logger] interface for Android
 * users.
 */
abstract class AbstractLogger(private val name: String) : Logger {

  abstract fun t(message: String, t: Throwable? = null, vararg args: Any?)

  abstract fun d(message: String, t: Throwable? = null, vararg args: Any?)

  abstract fun i(message: String, t: Throwable? = null, vararg args: Any?)

  abstract fun w(message: String, t: Throwable? = null, vararg args: Any?)

  abstract fun e(message: String, t: Throwable? = null, vararg args: Any?)

  override fun getName() = name

  override fun isTraceEnabled(marker: Marker?): Boolean = isTraceEnabled

  override fun isDebugEnabled(marker: Marker?): Boolean = isDebugEnabled

  override fun isInfoEnabled(marker: Marker?): Boolean = isInfoEnabled

  override fun isWarnEnabled(marker: Marker?): Boolean = isWarnEnabled

  override fun isErrorEnabled(marker: Marker?): Boolean = isErrorEnabled

  override fun trace(msg: String) = t(msg)

  override fun trace(format: String, arg: Any?) = t(format, null, arg)

  override fun trace(format: String, arg1: Any?, arg2: Any?) = t(format, null, arg1, arg2)

  override fun trace(format: String, vararg arguments: Any?) = t(format, null, *arguments)

  override fun trace(msg: String, t: Throwable?) = t(msg, t)

  override fun trace(marker: Marker, msg: String) = trace(msg)

  override fun trace(marker: Marker?, format: String, arg: Any?) = trace(format, arg)

  override fun trace(marker: Marker?, format: String, arg1: Any?, arg2: Any?) =
    trace(format, arg1, arg2)

  override fun trace(marker: Marker?, format: String, vararg arguments: Any?) =
    trace(format, *arguments)

  override fun trace(marker: Marker?, msg: String, t: Throwable?) = trace(msg, t)

  override fun debug(msg: String) = d(msg)

  override fun debug(format: String, arg: Any?) = d(format, null, arg)

  override fun debug(format: String, arg1: Any?, arg2: Any?) = d(format, null, arg1, arg2)

  override fun debug(format: String, vararg arguments: Any?) = d(format, null, *arguments)

  override fun debug(msg: String, t: Throwable?) = d(msg, t)

  override fun debug(marker: Marker, msg: String) = debug(msg)

  override fun debug(marker: Marker?, format: String, arg: Any?) = debug(format, arg)

  override fun debug(marker: Marker?, format: String, arg1: Any?, arg2: Any?) =
    debug(format, arg1, arg2)

  override fun debug(marker: Marker?, format: String, vararg arguments: Any?) =
    debug(format, *arguments)

  override fun debug(marker: Marker?, msg: String, t: Throwable?) = debug(msg, t)

  override fun info(msg: String) = i(msg)

  override fun info(format: String, arg: Any?) = i(format, null, arg)

  override fun info(format: String, arg1: Any?, arg2: Any?) = i(format, null, arg1, arg2)

  override fun info(format: String, vararg arguments: Any?) = i(format, null, *arguments)

  override fun info(msg: String, t: Throwable?) = i(msg, t)

  override fun info(marker: Marker, msg: String) = info(msg)

  override fun info(marker: Marker?, format: String, arg: Any?) = info(format, arg)

  override fun info(marker: Marker?, format: String, arg1: Any?, arg2: Any?) =
    info(format, arg1, arg2)

  override fun info(marker: Marker?, format: String, vararg arguments: Any?) =
    info(format, *arguments)

  override fun info(marker: Marker?, msg: String, t: Throwable?) = info(msg, t)

  override fun warn(msg: String) = w(msg)

  override fun warn(format: String, arg: Any?) = w(format, null, arg)

  override fun warn(format: String, arg1: Any?, arg2: Any?) = w(format, null, arg1, arg2)

  override fun warn(format: String, vararg arguments: Any?) = w(format, null, *arguments)

  override fun warn(msg: String, t: Throwable?) = w(msg, t)

  override fun warn(marker: Marker, msg: String) = warn(msg)

  override fun warn(marker: Marker?, format: String, arg: Any?) = warn(format, arg)

  override fun warn(marker: Marker?, format: String, arg1: Any?, arg2: Any?) =
    warn(format, arg1, arg2)

  override fun warn(marker: Marker?, format: String, vararg arguments: Any?) =
    warn(format, *arguments)

  override fun warn(marker: Marker?, msg: String, t: Throwable?) = warn(msg, t)

  override fun error(msg: String) = e(msg)

  override fun error(format: String, arg: Any?) = e(format, null, arg)

  override fun error(format: String, arg1: Any?, arg2: Any?) = e(format, null, arg1, arg2)

  override fun error(format: String, vararg arguments: Any?) = e(format, null, *arguments)

  override fun error(msg: String, t: Throwable?) = e(msg, t)

  override fun error(marker: Marker, msg: String) = error(msg)

  override fun error(marker: Marker?, format: String, arg: Any?) = error(format, arg)

  override fun error(marker: Marker?, format: String, arg1: Any?, arg2: Any?) =
    error(format, arg1, arg2)

  override fun error(marker: Marker?, format: String, vararg arguments: Any?) =
    error(format, *arguments)

  override fun error(marker: Marker?, msg: String, t: Throwable?) = error(msg, t)
}
