package app.passwordstore.gradle

import app.passwordstore.gradle.artifacts.CollectApksTask
import app.passwordstore.gradle.artifacts.CollectBundleTask
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.VariantOutputConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

@Suppress("Unused")
class RenameArtifactsPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.withPlugin("com.android.application") {
      project.extensions.getByType<ApplicationAndroidComponentsExtension>().run {
        onVariants { variant ->
          project.tasks.register<CollectApksTask>("collect${variant.name.capitalize()}Apks") {
            variantName.set(variant.name)
            apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
            builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
            outputDirectory.set(project.layout.projectDirectory.dir("outputs"))
          }
          project.tasks.register<CollectBundleTask>("collect${variant.name.capitalize()}Bundle") {
            val mainOutput =
              variant.outputs.single {
                it.outputType == VariantOutputConfiguration.OutputType.SINGLE
              }
            variantName.set(variant.name)
            versionName.set(mainOutput.versionName)
            bundleFile.set(variant.artifacts.get(SingleArtifact.BUNDLE))
            outputDirectory.set(project.layout.projectDirectory.dir("outputs"))
          }
        }
      }
    }
  }
}
