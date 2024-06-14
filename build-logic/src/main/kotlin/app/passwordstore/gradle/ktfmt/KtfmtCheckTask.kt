package app.passwordstore.gradle.ktfmt

import app.passwordstore.gradle.KtfmtPlugin
import com.facebook.ktfmt.format.Formatter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

@OptIn(ExperimentalCoroutinesApi::class)
abstract class KtfmtCheckTask : SourceTask() {

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  @get:IgnoreEmptyDirectories
  protected val inputFiles: FileCollection
    get() = super.getSource()

  @get:Internal abstract val projectDirectory: DirectoryProperty

  @TaskAction
  fun execute() {
    runBlocking(Dispatchers.IO.limitedParallelism(PARALLEL_TASK_LIMIT)) {
      coroutineScope {
        val results = inputFiles.map { async { checkFile(it) } }.awaitAll()
        if (results.any { (notFormatted, _) -> notFormatted }) {
          val prettyDiff =
            results
              .map { (_, diffs) -> diffs }
              .flatten()
              .joinToString(separator = "\n") { diff -> diff.toString() }
          throw GradleException("[ktfmt] Found unformatted files\n${prettyDiff}")
        }
      }
    }
  }

  private fun checkFile(input: File): Pair<Boolean, List<KtfmtDiffEntry>> {
    val originCode = input.readText()
    val formattedCode = Formatter.format(KtfmtPlugin.DEFAULT_FORMATTING_OPTIONS, originCode)
    val pathNormalizer = { file: File -> file.toRelativeString(projectDirectory.asFile.get()) }
    return (originCode != formattedCode) to
      KtfmtDiffer.computeDiff(input, formattedCode, pathNormalizer)
  }

  companion object {

    private const val PARALLEL_TASK_LIMIT = 4
  }
}
