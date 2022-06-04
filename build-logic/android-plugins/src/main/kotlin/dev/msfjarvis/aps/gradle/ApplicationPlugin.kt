@file:Suppress("UnstableApiUsage")

package dev.msfjarvis.aps.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import dev.msfjarvis.aps.gradle.flavors.FlavorDimensions
import dev.msfjarvis.aps.gradle.flavors.ProductFlavors
import dev.msfjarvis.aps.gradle.signing.configureBuildSigning
import dev.msfjarvis.aps.gradle.snapshot.SnapshotExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.the

@Suppress("Unused")
class ApplicationPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.pluginManager.apply(AppPlugin::class)
    AndroidCommon.configure(project)
    project.extensions.getByType<BaseAppModuleExtension>().run {
      val minifySwitch = project.providers.environmentVariable("DISABLE_MINIFY")

      adbOptions.installOptions("--user 0")

      dependenciesInfo {
        includeInBundle = false
        includeInApk = false
      }

      buildFeatures {
        viewBinding = true
        buildConfig = true
      }

      buildTypes {
        named("release") {
          isMinifyEnabled = !minifySwitch.isPresent
          setProguardFiles(
            listOf(
              "proguard-android-optimize.txt",
              "proguard-rules.pro",
              "proguard-rules-missing-classes.pro",
            )
          )
          buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "${project.isSnapshot()}")
        }
        named("debug") {
          applicationIdSuffix = ".debug"
          versionNameSuffix = "-debug"
          isMinifyEnabled = false
          buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "true")
        }
      }

      flavorDimensions.add(FlavorDimensions.FREE)
      productFlavors {
        register(ProductFlavors.FREE) {}
        register(ProductFlavors.NON_FREE) {}
      }

      project.configureBuildSigning()
    }

    project.dependencies {
      extensions.add("snapshot", SnapshotExtension::class.java)
      the<SnapshotExtension>().snapshot = project.isSnapshot()
    }
  }

  private fun Project.isSnapshot(): Boolean {
    with(project.providers) {
      val workflow = environmentVariable("GITHUB_WORKFLOW")
      val snapshot = environmentVariable("SNAPSHOT")
      return workflow.isPresent || snapshot.isPresent
    }
  }
}
