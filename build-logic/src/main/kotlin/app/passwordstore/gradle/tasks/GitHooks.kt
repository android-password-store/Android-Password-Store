/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle.tasks

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE
import java.nio.file.attribute.PosixFilePermission.GROUP_READ
import java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OTHERS_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GitHooks : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val hookSource: RegularFileProperty

  @get:OutputFile abstract val hookOutput: RegularFileProperty

  @TaskAction
  fun install() {
    Files.copy(
      hookSource.asFile.get().toPath(),
      hookOutput.asFile.get().toPath(),
      StandardCopyOption.REPLACE_EXISTING,
    )
    Files.setPosixFilePermissions(
      hookOutput.asFile.get().toPath(),
      setOf(
        OWNER_READ,
        OWNER_WRITE,
        OWNER_EXECUTE,
        GROUP_READ,
        GROUP_EXECUTE,
        OTHERS_READ,
        OTHERS_EXECUTE,
      ),
    )
  }
}
