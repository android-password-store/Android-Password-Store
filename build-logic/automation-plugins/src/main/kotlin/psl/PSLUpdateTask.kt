/*
 * Copyright © 2014-2022 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package psl

import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class PSLUpdateTask : DefaultTask() {
  @get:Input abstract val pslData: Property<PublicSuffixListData>
  @get:OutputFile abstract val outputFile: RegularFileProperty

  @TaskAction
  fun updatePSL() {
    writeListToDisk(outputFile.get(), pslData.get())
  }

  private fun writeListToDisk(destination: RegularFile, data: PublicSuffixListData) {
    val fileSink = destination.asFile.sink()

    fileSink.buffer().use { sink ->
      sink.writeInt(data.totalRuleBytes)

      for (domain in data.sortedRules) {
        sink.write(domain).writeByte('\n'.toInt())
      }

      sink.writeInt(data.totalExceptionRuleBytes)

      for (domain in data.sortedExceptionRules) {
        sink.write(domain).writeByte('\n'.toInt())
      }
    }
  }
}
