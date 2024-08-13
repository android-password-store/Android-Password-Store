/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle.artifacts

import com.android.build.api.variant.BuiltArtifactsLoader
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Task to collect APKs in a given [outputDirectory]. */
@CacheableTask
abstract class CollectApksTask : DefaultTask() {
  @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val apkFolder: DirectoryProperty

  @get:Input abstract val variantName: Property<String>

  @get:Internal abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun run() {
    val outputDir = outputDirectory.asFile.get()
    outputDir.mkdirs()
    val builtArtifacts =
      builtArtifactsLoader.get().load(apkFolder.get()) ?: throw RuntimeException("Cannot load APKs")
    builtArtifacts.elements.forEach { artifact ->
      Files.copy(
        Paths.get(artifact.outputFile),
        outputDir.resolve("APS-${variantName.get()}-${artifact.versionName}.apk").toPath(),
        StandardCopyOption.REPLACE_EXISTING,
      )
    }
  }
}
