package app.passwordstore.gradle

import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

@Suppress("Unused")
class LibraryPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.run {
      apply(LibraryPlugin::class)
      apply(KotlinCommonPlugin::class)
    }
    AndroidCommon.configure(project)
  }
}
