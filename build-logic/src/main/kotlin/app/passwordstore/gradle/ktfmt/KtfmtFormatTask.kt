package app.passwordstore.gradle.ktfmt

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.FormattingOptions
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

@OptIn(ExperimentalCoroutinesApi::class)
abstract class KtfmtFormatTask : SourceTask() {

  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:InputFiles
  @get:IgnoreEmptyDirectories
  protected val inputFiles: FileCollection
    get() = super.getSource()

  @TaskAction
  fun execute() {
    runBlocking(Dispatchers.IO.limitedParallelism(PARALLEL_TASK_LIMIT)) {
      coroutineScope { inputFiles.map { async { formatFile(it) } }.awaitAll() }
    }
  }

  private fun formatFile(input: File) {
    val originCode = input.readText()
    val formattedCode =
      Formatter.format(
        FormattingOptions(
          style = FormattingOptions.Style.GOOGLE,
          maxWidth = 100,
          continuationIndent = 2,
        ),
        originCode
      )
    if (originCode != formattedCode) {
      input.writeText(formattedCode)
    }
  }

  companion object {

    private const val PARALLEL_TASK_LIMIT = 4
  }
}
