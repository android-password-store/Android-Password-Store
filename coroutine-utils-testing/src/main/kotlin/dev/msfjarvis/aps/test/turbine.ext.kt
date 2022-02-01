/*
 * Copyright © 2014-2022 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.test

import app.cash.turbine.FlowTurbine
import app.cash.turbine.test
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Wrapper for [test] that implements compatibility with kotlinx.coroutines 1.6.0
 *
 * @see "https://github.com/cashapp/turbine/issues/42#issuecomment-1000317026"
 */
@ExperimentalTime
@ExperimentalCoroutinesApi
public suspend fun <T> Flow<T>.test2(
  timeout: Duration = 1.seconds,
  validate: suspend FlowTurbine<T>.() -> Unit,
) {
  val testScheduler = coroutineContext[TestCoroutineScheduler]
  return if (testScheduler == null) {
    test(timeout, validate)
  } else {
    flowOn(UnconfinedTestDispatcher(testScheduler)).test(timeout, validate)
  }
}
