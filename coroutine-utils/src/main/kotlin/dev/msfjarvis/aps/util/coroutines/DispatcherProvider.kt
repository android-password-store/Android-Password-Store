/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.coroutines

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Interface to allow abstracting individual [CoroutineDispatcher]s out of class dependencies. */
public interface DispatcherProvider {

  public fun main(): CoroutineDispatcher = Dispatchers.Main
  public fun default(): CoroutineDispatcher = Dispatchers.Default
  public fun io(): CoroutineDispatcher = Dispatchers.IO
  public fun unconfined(): CoroutineDispatcher = Dispatchers.Unconfined
}

/** Concrete type for [DispatcherProvider] with all the defaults from the class. */
public class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider
