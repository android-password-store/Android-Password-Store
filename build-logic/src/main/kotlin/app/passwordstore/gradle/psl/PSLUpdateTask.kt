/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package app.passwordstore.gradle.psl

import app.passwordstore.gradle.OkHttp
import java.util.TreeSet
import okhttp3.Request
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Based on PublicSuffixListGenerator from OkHttp:
 * https://github.com/square/okhttp/blob/3ad1912f783e108b3d0ad2c4a5b1b89b827e4db9/okhttp/src/jvmTest/java/okhttp3/internal/publicsuffix/PublicSuffixListGenerator.java
 */
abstract class PSLUpdateTask : DefaultTask() {
  @get:OutputFile abstract val outputFile: RegularFileProperty

  @TaskAction
  fun updatePSL() {
    val pslData = fetchPublicSuffixList()
    writeListToDisk(outputFile.get(), pslData)
  }

  private fun fetchPublicSuffixList(): PublicSuffixListData {
    val request =
      Request.Builder().url("https://publicsuffix.org/list/public_suffix_list.dat").build()

    OkHttp.CLIENT.newCall(request).execute().use { response ->
      val source = requireNotNull(response.body).source()

      val data = PublicSuffixListData()

      while (!source.exhausted()) {
        val line = source.readUtf8LineStrict()

        if (line.trim { it <= ' ' }.isEmpty() || line.startsWith("//")) {
          continue
        }

        if (line.contains(WILDCARD_CHAR)) {
          assertWildcardRule(line)
        }

        var rule = line.encodeUtf8()

        if (rule.startsWith(EXCEPTION_RULE_MARKER)) {
          rule = rule.substring(1)
          // We use '\n' for end of value.
          data.totalExceptionRuleBytes += rule.size + 1
          data.sortedExceptionRules.add(rule)
        } else {
          data.totalRuleBytes += rule.size + 1 // We use '\n' for end of value.
          data.sortedRules.add(rule)
        }
      }
      return data
    }
  }

  @Suppress("TooGenericExceptionThrown", "ThrowsCount")
  private fun assertWildcardRule(rule: String) {
    if (rule.indexOf(WILDCARD_CHAR) != 0) {
      throw RuntimeException("Wildcard is not not in leftmost position")
    }

    if (rule.indexOf(WILDCARD_CHAR, 1) != -1) {
      throw RuntimeException("Rule contains multiple wildcards")
    }

    if (rule.length == 1) {
      throw RuntimeException("Rule wildcards the first level")
    }
  }

  private fun writeListToDisk(destination: RegularFile, data: PublicSuffixListData) {
    val fileSink = destination.asFile.sink()

    fileSink.buffer().use { sink ->
      sink.writeInt(data.totalRuleBytes)

      for (domain in data.sortedRules) {
        sink.write(domain).writeByte('\n'.code)
      }

      sink.writeInt(data.totalExceptionRuleBytes)

      for (domain in data.sortedExceptionRules) {
        sink.write(domain).writeByte('\n'.code)
      }
    }
  }

  data class PublicSuffixListData(
    var totalRuleBytes: Int = 0,
    var totalExceptionRuleBytes: Int = 0,
    val sortedRules: TreeSet<ByteString> = TreeSet(),
    val sortedExceptionRules: TreeSet<ByteString> = TreeSet(),
  )

  private companion object {
    private const val WILDCARD_CHAR = "*"
    private val EXCEPTION_RULE_MARKER = "!".encodeUtf8()
  }
}
