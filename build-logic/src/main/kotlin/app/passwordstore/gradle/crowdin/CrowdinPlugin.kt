/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle.crowdin

import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

@Suppress("Unused")
class CrowdinDownloadPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    with(project) {
      val extension = extensions.create<CrowdinExtension>("crowdin")
      val login = providers.environmentVariable("CROWDIN_LOGIN")
      val key = providers.environmentVariable("CROWDIN_PROJECT_KEY")
      val buildOnApi =
        if (login.isPresent && key.isPresent) {
          tasks.register<BuildOnApiTask>("buildOnApi") {
            crowdinIdentifier.set(extension.crowdinIdentifier)
            crowdinLogin.set(login)
            crowdinKey.set(key)
          }
        } else {
          null
        }
      val downloadCrowdin =
        tasks.register<Download>("downloadCrowdin") {
          if (buildOnApi != null) dependsOn(buildOnApi)
          src(
            "https://crowdin.com/backend/download/project/${extension.crowdinIdentifier.get()}.zip"
          )
          dest(layout.buildDirectory.file("translations.zip"))
          overwrite(true)
        }
      val extractCrowdin =
        tasks.register<Copy>("extractCrowdin") {
          from(zipTree(downloadCrowdin.map { it.outputFiles.first() }))
          into(layout.buildDirectory.dir("translations"))
        }
      val extractStrings =
        tasks.register<Copy>("extractStrings") {
          from(extractCrowdin.map { it.destinationDir })
          into(layout.projectDirectory.dir("src"))
        }
      val removeIncompleteStrings =
        tasks.register<StringCleanupTask>("removeIncompleteStrings") {
          sourceDirectory.set(
            objects.directoryProperty().fileProvider(extractStrings.map { it.destinationDir })
          )
        }
      tasks.register<Delete>("crowdin") {
        dependsOn(removeIncompleteStrings)
        delete =
          if (extension.skipCleanup.getOrElse(false)) {
            emptySet()
          } else {
            setOf(extractStrings.map { it.source }, downloadCrowdin.map { it.outputFiles })
          }
      }
    }
  }
}
