/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.autofill.oreo.ui

import android.content.Context
import android.content.IntentSender
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

@RequiresApi(Build.VERSION_CODES.O)
@Suppress("UNUSED_PARAMETER")
class AutofillSmsActivity : AppCompatActivity() {

  companion object {

    fun shouldOfferFillFromSms(context: Context): Boolean {
      return false
    }

    fun makeFillOtpFromSmsIntentSender(context: Context): IntentSender {
      throw NotImplementedError("Filling OTPs from SMS requires non-free dependencies")
    }
  }
}
