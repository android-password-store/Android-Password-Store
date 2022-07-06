package dev.msfjarvis.aps.gradle

import com.android.build.gradle.TestedExtension
import dev.msfjarvis.aps.gradle.flavors.configureSlimTests
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

@Suppress("UnstableApiUsage")
object AndroidCommon {
  fun configure(project: Project) {
    project.extensions.configure<TestedExtension> {
      setCompileSdkVersion(32)
      defaultConfig {
        minSdk = 23
        targetSdk = 31
      }

      sourceSets {
        named("main") { java.srcDirs("src/main/kotlin") }
        named("test") { java.srcDirs("src/test/kotlin") }
        named("androidTest") { java.srcDirs("src/androidTest/kotlin") }
      }

      packagingOptions {
        resources.excludes.add("**/*.version")
        resources.excludes.add("**/*.txt")
        resources.excludes.add("**/*.kotlin_module")
        resources.excludes.add("**/plugin.properties")
        resources.excludes.add("**/META-INF/AL2.0")
        resources.excludes.add("**/META-INF/LGPL2.1")
      }

      compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
      }

      testOptions {
        animationsDisabled = true
        unitTests.isReturnDefaultValues = true
      }

      project.configureSlimTests()
    }
  }
}
