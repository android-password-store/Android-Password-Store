/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

// It's okay if this stays unused for the most part since it is development tooling.
@file:Suppress("Unused")

package app.passwordstore.util

import android.os.Looper
import kotlin.time.measureTime
import logcat.logcat

/**
 * Small helper to execute a given [block] and log the time it took to execute it. Intended for use
 * in day-to-day perf investigations and code using it should probably not be shipped.
 */
inline fun <T> logExecutionTime(tag: String, crossinline block: () -> T): T {
  val res: T
  val duration = measureTime { res = block() }
  logcat(tag) { "Finished in ${duration.inWholeMilliseconds}ms" }
  return res
}

/**
 * Throws if called on the main thread, used to ensure an operation being offloaded to a background
 * thread is correctly being moved off the main thread.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun checkMainThread() {
  require(Looper.myLooper() != Looper.getMainLooper()) {
    "This operation must not run on the main thread"
  }
}
