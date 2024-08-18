/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle

import java.util.concurrent.TimeUnit
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

object OkHttp {
  private val certificatePinner =
    CertificatePinner.Builder()
      .add(
        "publicsuffix.org",
        "sha256/Ov/MkC2OkVtTp9MdY+uXOKAuV2Birfdeazval8seMZM=",
        "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0=",
        "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=",
      )
      .build()
  val CLIENT =
    OkHttpClient.Builder()
      .connectTimeout(5, TimeUnit.MINUTES)
      .writeTimeout(5, TimeUnit.MINUTES)
      .readTimeout(5, TimeUnit.MINUTES)
      .callTimeout(10, TimeUnit.MINUTES)
      .certificatePinner(certificatePinner)
      .build()
}
