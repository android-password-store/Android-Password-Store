/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.github.ajalt.timberkt.v

class ChromeCompatFix : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val forceRootNodePopulation = Runnable {
        val rootPackageName = rootInActiveWindow?.packageName.toString()
        v { "$rootPackageName: forced root node population" }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        handler.removeCallbacks(forceRootNodePopulation)
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                // WINDOW_STATE_CHANGED: Triggered on long press in a text field, replacement for
                //                       the missing Autofill action menu item.
                // ANNOUNCEMENT:         Triggered when a password field is selected.
                //
                // These events are triggered only by user actions and thus don't need to be handled
                // with debounce. However, they only trigger Autofill popups on the *next* input
                // field selected by the user.
                forceRootNodePopulation.run()
                v { "${event.packageName} (${AccessibilityEvent.eventTypeToString(event.eventType)}): forced root node population" }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // WINDOW_CONTENT_CHANGED: Triggered whenever the page contents change.
                //
                // This event is triggered many times during page load, which makes a debounce
                // necessary to prevent huge performance regressions in Chrome. However, it is the
                // only event that reliably runs before the user selects a text field.
                handler.postDelayed(forceRootNodePopulation, 300)
                v { "${event.packageName} (${AccessibilityEvent.eventTypeToString(event.eventType)}): debounced root node population" }
            }
        }
    }

    override fun onInterrupt() {}
}

