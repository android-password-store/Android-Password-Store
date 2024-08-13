/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package app.passwordstore.util.git.operation

import androidx.appcompat.app.AppCompatActivity
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.ResetCommand

class ResetToRemoteOperation(callingActivity: AppCompatActivity, remoteBranch: String) :
  GitOperation(callingActivity) {

  override val commands: Array<GitCommand<out Any>> =
    arrayOf(
      // Fetch everything from the origin remote
      git.fetch().setRemote("origin").setRemoveDeletedRefs(true),
      // Force-create $remoteBranch if it doesn't exist. This covers the case where a branch name is
      // changed.
      git.branchCreate().setName(remoteBranch).setForce(true),
      git.checkout().setName(remoteBranch).setForce(true).setUpstreamMode(TRACK),
      // Do a hard reset to the remote branch. Equivalent to git reset --hard
      // origin/$remoteBranch
      git.reset().setRef("origin/$remoteBranch").setMode(ResetCommand.ResetType.HARD),
    )
}
