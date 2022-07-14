package app.passwordstore.gradle

import app.passwordstore.gradle.tasks.GitHooks
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

@Suppress("Unused")
class GitHooksPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.tasks.register<GitHooks>("installGitHooks") {
      val projectDirectory = project.layout.projectDirectory
      hookScript.set(projectDirectory.file("scripts/pre-push-hook.sh").asFile.readText())
      hookOutput.set(projectDirectory.file(".git/hooks/pre-push").asFile)
    }
  }
}
