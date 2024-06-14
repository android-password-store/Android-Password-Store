package app.passwordstore.gradle

import app.passwordstore.gradle.ktfmt.KtfmtCheckTask
import app.passwordstore.gradle.ktfmt.KtfmtFormatTask
import com.facebook.ktfmt.format.FormattingOptions
import java.util.concurrent.Callable
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class KtfmtPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    val input = Callable {
      target.layout.projectDirectory.asFileTree.filter { file ->
        file.extension == "kt" || file.extension == "kts" && !file.canonicalPath.contains("build/")
      }
    }
    target.tasks.register<KtfmtFormatTask>("ktfmtFormat") { source(input) }
    target.tasks.register<KtfmtCheckTask>("ktfmtCheck") {
      source(input)
      projectDirectory.set(target.layout.projectDirectory)
    }
  }

  companion object {
    val DEFAULT_FORMATTING_OPTIONS =
      FormattingOptions(
        maxWidth = FormattingOptions.DEFAULT_MAX_WIDTH,
        blockIndent = 2,
        continuationIndent = 2,
        removeUnusedImports = true,
        debuggingPrintOpsAfterFormatting = false,
        manageTrailingCommas = true,
      )
  }
}
