/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.test

import dev.msfjarvis.aps.util.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit [TestWatcher] to correctly handle setting and resetting a given [testDispatcher] for tests.
 */
@ExperimentalCoroutinesApi
public class CoroutineTestRule(
  public val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(TestCoroutineScheduler()),
) : TestWatcher() {

  public val testDispatcherProvider: DispatcherProvider =
    object : DispatcherProvider {
      override fun default(): CoroutineDispatcher = testDispatcher
      override fun io(): CoroutineDispatcher = testDispatcher
      override fun main(): CoroutineDispatcher = testDispatcher
      override fun unconfined(): CoroutineDispatcher = testDispatcher
    }

  override fun starting(description: Description?) {
    super.starting(description)
    Dispatchers.setMain(testDispatcher)
  }

  override fun finished(description: Description?) {
    super.finished(description)
    Dispatchers.resetMain()
  }
}
