/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package crowdin

import de.undercouch.gradle.tasks.download.Download
import java.io.File
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.w3c.dom.Document

private const val EXCEPTION_MESSAGE =
  """Applying `crowdin-plugin` requires a projectName to be configured via the "crowdin" extension."""
private const val CROWDIN_BUILD_API_URL =
  "https://api.crowdin.com/api/project/%s/export?login=%s&account-key=%s"

class CrowdinDownloadPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    with(project) {
      val buildDirectory = layout.buildDirectory.asFile.forUseAtConfigurationTime().get()
      val extension = extensions.create<CrowdinExtension>("crowdin")
      afterEvaluate {
        val projectName = extension.projectName
        if (projectName.isEmpty()) {
          throw GradleException(EXCEPTION_MESSAGE)
        }
        val buildOnApi =
          tasks.register("buildOnApi") {
            doLast {
              val login = providers.environmentVariable("CROWDIN_LOGIN").forUseAtConfigurationTime()
              val key =
                providers.environmentVariable("CROWDIN_PROJECT_KEY").forUseAtConfigurationTime()
              if (!login.isPresent) {
                throw GradleException("CROWDIN_LOGIN environment variable must be set")
              }
              if (!key.isPresent) {
                throw GradleException("CROWDIN_PROJECT_KEY environment variable must be set")
              }
              val client =
                OkHttpClient.Builder()
                  .connectTimeout(5, TimeUnit.MINUTES)
                  .writeTimeout(5, TimeUnit.MINUTES)
                  .readTimeout(5, TimeUnit.MINUTES)
                  .callTimeout(10, TimeUnit.MINUTES)
                  .build()
              val url = CROWDIN_BUILD_API_URL.format(projectName, login.get(), key.get())
              val request = Request.Builder().url(url).get().build()
              client.newCall(request).execute()
            }
          }
        val downloadCrowdin =
          tasks.register<Download>("downloadCrowdin") {
            dependsOn(buildOnApi)
            src("https://crowdin.com/backend/download/project/$projectName.zip")
            dest("$buildDirectory/translations.zip")
            overwrite(true)
          }
        val extractCrowdin =
          tasks.register<Copy>("extractCrowdin") {
            dependsOn(downloadCrowdin)
            doFirst { File(buildDir, "translations").deleteRecursively() }
            from(zipTree("$buildDirectory/translations.zip"))
            into("$buildDirectory/translations")
          }
        val extractStrings =
          tasks.register<Copy>("extractStrings") {
            dependsOn(extractCrowdin)
            from("$buildDirectory/translations/")
            into("${projectDir}/src/")
            setFinalizedBy(setOf("removeIncompleteStrings"))
          }
        tasks.register("removeIncompleteStrings") {
          doLast {
            val sourceSets = arrayOf("main", "nonFree")
            for (sourceSet in sourceSets) {
              val fileTreeWalk = projectDir.resolve("src/$sourceSet").walkTopDown()
              val valuesDirectories =
                fileTreeWalk.filter { it.isDirectory }.filter { it.name.startsWith("values") }
              val stringFiles = fileTreeWalk.filter { it.name == "strings.xml" }
              val sourceFile =
                stringFiles.firstOrNull { it.path.endsWith("values/strings.xml") }
                  ?: throw GradleException("No root strings.xml found in '$sourceSet' sourceSet")
              val sourceDoc = parseDocument(sourceFile)
              val baselineStringCount = countStrings(sourceDoc)
              val threshold = 0.80 * baselineStringCount
              stringFiles.forEach { file ->
                if (file != sourceFile) {
                  val doc = parseDocument(file)
                  val stringCount = countStrings(doc)
                  if (stringCount < threshold) {
                    file.delete()
                  }
                }
              }
              valuesDirectories.forEach { dir ->
                if (dir.listFiles().isNullOrEmpty()) {
                  dir.delete()
                }
              }
            }
          }
        }
        tasks.register("crowdin") {
          dependsOn(extractStrings)
          if (!extension.skipCleanup) {
            doLast {
              File("$buildDirectory/translations").deleteRecursively()
              File("$buildDirectory/nonFree-translations").deleteRecursively()
              File("$buildDirectory/translations.zip").delete()
            }
          }
        }
      }
    }
  }

  private fun parseDocument(file: File): Document {
    val dbFactory = DocumentBuilderFactory.newInstance()
    val documentBuilder = dbFactory.newDocumentBuilder()
    return documentBuilder.parse(file)
  }

  private fun countStrings(document: Document): Int {
    // Normalization is beneficial for us
    // https://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
    document.documentElement.normalize()
    return document.getElementsByTagName("string").length
  }
}
