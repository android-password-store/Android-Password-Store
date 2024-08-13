/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.gradle.versioning

import com.github.zafarkhaja.semver.Version
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class VersioningTask : DefaultTask() {
  @get:Input abstract val semverString: Property<String>

  @get:OutputFile abstract val propertyFile: RegularFileProperty

  /** Generate the Android 'versionCode' property */
  private fun Version.androidCode(): Long {
    return majorVersion() * 1_00_00 + minorVersion() * 1_00 + patchVersion()
  }

  private fun Version.toPropFileText(): String {
    val newVersionCode = androidCode()
    val newVersionName = toString()
    return buildString {
      appendLine(VERSIONING_PROP_COMMENT)
      append(VERSIONING_PROP_VERSION_CODE)
      append('=')
      appendLine(newVersionCode)
      append(VERSIONING_PROP_VERSION_NAME)
      append('=')
      appendLine(newVersionName)
    }
  }

  override fun getGroup(): String {
    return "versioning"
  }

  @TaskAction
  fun execute() {
    propertyFile.get().asFile.writeText(Version.parse(semverString.get()).toPropFileText())
  }
}
