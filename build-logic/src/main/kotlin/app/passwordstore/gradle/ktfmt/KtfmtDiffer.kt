package app.passwordstore.gradle.ktfmt

import com.github.difflib.DiffUtils
import com.github.difflib.patch.ChangeDelta
import com.github.difflib.patch.DeleteDelta
import com.github.difflib.patch.InsertDelta
import java.nio.file.Path
import kotlin.io.path.readText

object KtfmtDiffer {
  fun computeDiff(
    inputFile: Path,
    formattedCode: String,
    pathNormalizer: (Path) -> String,
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
}
