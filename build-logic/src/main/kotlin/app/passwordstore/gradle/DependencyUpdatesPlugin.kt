package app.passwordstore.gradle

import com.github.benmanes.gradle.versions.VersionsPlugin
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import nl.littlerobots.vcu.plugin.VersionCatalogUpdateExtension
import nl.littlerobots.vcu.plugin.VersionCatalogUpdatePlugin
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

@Suppress("Unused")
class DependencyUpdatesPlugin : Plugin<Settings> {

  override fun apply(settings: Settings) {
    settings.gradle.allprojects {
      if (rootProject == this) {
        pluginManager.apply(VersionCatalogUpdatePlugin::class)
        extensions.configure<VersionCatalogUpdateExtension> { keep.keepUnusedLibraries.set(true) }
        pluginManager.apply(VersionsPlugin::class)
        tasks.withType<DependencyUpdatesTask> {
          rejectVersionIf {
            when (candidate.group) {
              "commons-codec",
              "org.eclipse.jgit" -> true
              else -> false
            }
          }
          checkForGradleUpdate = false
        }
      }
    }
  }
}
