@file:Suppress("UnstableApiUsage")

package app.passwordstore.gradle

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishPlugin
import com.vanniktech.maven.publish.SonatypeHost
import me.tylerbwong.gradle.metalava.Documentation
import me.tylerbwong.gradle.metalava.extension.MetalavaExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

@Suppress("Unused")
class PublishedAndroidLibraryPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.plugins.run {
      apply(LibraryPlugin::class)
      apply(MavenPublishPlugin::class)
      apply(SigningPlugin::class)
      apply("me.tylerbwong.gradle.metalava")
    }
    project.extensions.getByType<MavenPublishBaseExtension>().run {
      publishToMavenCentral(SonatypeHost.DEFAULT, true)
      signAllPublications()
    }
    project.afterEvaluate {
      project.extensions.getByType<SigningExtension>().run {
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
      }
    }
    project.extensions.getByType<MetalavaExtension>().run {
      documentation.set(Documentation.PUBLIC)
      inputKotlinNulls.set(true)
      outputKotlinNulls.set(true)
      reportLintsAsErrors.set(true)
      reportWarningsAsErrors.set(true)
    }
  }
}
