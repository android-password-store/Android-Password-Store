/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.util.git

import java.time.Instant

/**
 * Basic information about a git commit.
 *
 * @property hash full-length hash of the commit object.
 * @property shortMessage the commit's short message (i.e. title line).
 * @property authorName name of the commit's author without email address.
 * @property time time when the commit was created.
 */
data class GitCommit(
  val hash: String,
  val shortMessage: String,
  val authorName: String,
  val time: Instant
)
