/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.github.ajalt.timberkt.d
import kotlin.system.measureTimeMillis

class ChromeCompatFix : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val time = measureTimeMillis { rootInActiveWindow }
        d { "${event.packageName} (${AccessibilityEvent.eventTypeToString(event.eventType)}): $time ms" }
    }

    override fun onInterrupt() {}
}

