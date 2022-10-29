package app.passwordstore.gradle

import app.passwordstore.gradle.ktfmt.KtfmtCheckTask
import app.passwordstore.gradle.ktfmt.KtfmtFormatTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class KtfmtPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.tasks.register<KtfmtFormatTask>("ktfmtFormat") {
      source =
        project.layout.projectDirectory.asFileTree
          .filter { file ->
            file.extension == "kt" ||
              file.extension == "kts" && !file.canonicalPath.contains("build")
          }
          .asFileTree
    }
    target.tasks.register<KtfmtCheckTask>("ktfmtCheck") {
      source =
        project.layout.projectDirectory.asFileTree
          .filter { file ->
            file.extension == "kt" ||
              file.extension == "kts" && !file.canonicalPath.contains("build")
          }
          .asFileTree
      projectDirectory.set(target.layout.projectDirectory)
    }
  }
}
