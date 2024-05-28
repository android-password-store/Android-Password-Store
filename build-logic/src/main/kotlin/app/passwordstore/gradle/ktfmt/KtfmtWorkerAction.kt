package app.passwordstore.gradle.ktfmt

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.FormattingOptions
import java.io.File
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.logging.slf4j.DefaultContextAwareTaskLogger
import org.gradle.workers.WorkAction

abstract class KtfmtWorkerAction : WorkAction<KtfmtWorkerParameters> {
  private val logger: Logger =
    DefaultContextAwareTaskLogger(Logging.getLogger(KtfmtFormatTask::class.java))
  private val files: List<File> = parameters.files.toList()
  private val projectDirectory: File = parameters.projectDirectory.asFile.get()
  private val name: String = parameters.name.get()

  override fun execute() {
    try {
      files.forEach { file ->
        val sourceText = file.readText()
        val relativePath = file.toRelativeString(projectDirectory)

        logger.log(LogLevel.DEBUG, "$name checking format: $relativePath")

        val formattedText =
          Formatter.format(
            FormattingOptions(
              style = FormattingOptions.Style.GOOGLE,
              maxWidth = FormattingOptions.DEFAULT_MAX_WIDTH,
              blockIndent = 2,
              continuationIndent = 2,
              removeUnusedImports = true,
              debuggingPrintOpsAfterFormatting = false,
              manageTrailingCommas = true,
            ),
            sourceText,
          )

        if (!formattedText.contentEquals(sourceText)) {
          logger.log(LogLevel.QUIET, "${file.toRelativeString(projectDirectory)}: Format fixed")
          file.writeText(formattedText)
        }
      }
    } catch (t: Throwable) {
      throw Exception("format worker execution error", t)
    }
  }
}
