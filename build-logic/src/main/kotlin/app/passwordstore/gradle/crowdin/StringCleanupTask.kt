package app.passwordstore.gradle.crowdin

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.w3c.dom.Document

@DisableCachingByDefault(because = "The task runs quickly and has complicated semantics")
abstract class StringCleanupTask : DefaultTask() {

  @get:InputDirectory abstract val sourceDirectory: DirectoryProperty

  @TaskAction
  fun clean() {
    val sourceSets = arrayOf("main", "nonFree")
    for (sourceSet in sourceSets) {
      val fileTreeWalk = sourceDirectory.dir("$sourceSet/res").get().asFile.walkTopDown()
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
