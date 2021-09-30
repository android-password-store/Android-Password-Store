/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.git.sshj

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.userauth.UserAuthException

/** Workaround for https://msfjarvis.dev/aps/issue/1164 */
open class ContinuationContainerActivity : AppCompatActivity {

  constructor() : super()
  constructor(@LayoutRes layoutRes: Int) : super(layoutRes)

  var stashedCont: Continuation<Intent>? = null

  val continueAfterUserInteraction =
    registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
      stashedCont?.let { cont ->
        stashedCont = null
        val data = result.data
        if (data != null) cont.resume(data)
        else cont.resumeWithException(UserAuthException(DisconnectReason.AUTH_CANCELLED_BY_USER))
      }
    }
}
