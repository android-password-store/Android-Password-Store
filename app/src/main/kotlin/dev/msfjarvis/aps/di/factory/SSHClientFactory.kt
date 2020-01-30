/*
 * Copyright © 2019-2020 The Android Password Store Authors. All Rights Reserved.
 *  SPDX-License-Identifier: GPL-3.0-only
 *
 */

package dev.msfjarvis.aps.di.factory

import android.content.Context
import net.schmizz.sshj.SSHClient
import java.io.File

object SSHClientFactory {
  fun provideSSHClient(context: Context): SSHClient {
    val client = SSHClient()
    client.loadKnownHosts(File(context.filesDir, "known_hosts"))
    return client
  }
}
