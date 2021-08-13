/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset.UTC

/**
 * Implementation of [UserClock] that is fixed to [Instant.EPOCH] for deterministic time-based tests
 */
class TestUserClock(instant: Instant) : UserClock() {

  constructor() : this(Instant.EPOCH)

  private var clock = fixed(instant, UTC)

  override fun withZone(zone: ZoneId): Clock = clock.withZone(zone)

  override fun getZone(): ZoneId = UTC

  override fun instant(): Instant = clock.instant()
}
