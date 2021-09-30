/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.util.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * A subclass of [Clock] that uses [Clock.systemDefaultZone] to get a clock that works for the
 * user's current time zone.
 */
public open class UserClock @Inject constructor() : Clock() {

  private val clock = systemDefaultZone()

  override fun withZone(zone: ZoneId): Clock = clock.withZone(zone)

  override fun getZone(): ZoneId = clock.zone

  override fun instant(): Instant = clock.instant()
}
