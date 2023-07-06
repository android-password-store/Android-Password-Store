package app.passwordstore.gradle

import app.passwordstore.gradle.ktfmt.KtfmtCheckTask
import app.passwordstore.gradle.ktfmt.KtfmtFormatTask
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
}
