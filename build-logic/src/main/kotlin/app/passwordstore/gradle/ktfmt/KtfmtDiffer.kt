package app.passwordstore.gradle.ktfmt

import com.github.difflib.DiffUtils
import com.github.difflib.patch.ChangeDelta
import com.github.difflib.patch.DeleteDelta
import com.github.difflib.patch.InsertDelta
import java.io.File
import org.gradle.api.logging.Logger

object KtfmtDiffer {
  fun computeDiff(
    inputFile: File,
    formattedCode: String,
    pathNormalizer: (File) -> String
  ): List<KtfmtDiffEntry> {
    val originCode = inputFile.readText()
    return DiffUtils.diff(originCode, formattedCode, null).deltas.map {
      val line = it.source.position + 1
      val message: String =
        when (it) {
          is ChangeDelta -> "Line changed: ${it.source.lines.first()}"
          is DeleteDelta -> "Line deleted"
          is InsertDelta -> "Line added"
          else -> ""
        }
      KtfmtDiffEntry(pathNormalizer(inputFile), line, message)
    }
  }

  fun printDiff(entries: List<KtfmtDiffEntry>, logger: Logger) {
    entries.forEach { entry ->
      logger.error("${entry.input}:${entry.lineNumber} - ${entry.message}")
    }
  }
}
