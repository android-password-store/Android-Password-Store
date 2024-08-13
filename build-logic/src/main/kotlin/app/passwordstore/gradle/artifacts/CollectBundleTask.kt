/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle.artifacts

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class CollectBundleTask : DefaultTask() {
  @get:InputFile abstract val bundleFile: RegularFileProperty

  @get:Input abstract val variantName: Property<String>

  @get:Input abstract val versionName: Property<String>

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun taskAction() {
    val outputDir = outputDirectory.asFile.get()
    outputDir.mkdirs()
    Files.copy(
      bundleFile.get().asFile.toPath(),
      outputDir.resolve("APS-${variantName.get()}-${versionName.get()}.aab").toPath(),
      StandardCopyOption.REPLACE_EXISTING,
    )
  }
}
