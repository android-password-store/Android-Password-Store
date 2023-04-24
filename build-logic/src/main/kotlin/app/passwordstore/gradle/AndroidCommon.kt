package app.passwordstore.gradle

import app.passwordstore.gradle.flavors.configureSlimTests
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
import com.android.build.gradle.TestedExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType

object AndroidCommon {
  fun configure(project: Project) {
    project.extensions.configure<TestedExtension> {
      setCompileSdkVersion(33)
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
      project.tasks.withType<Test> {
        jvmArgs(
          "--add-opens=java.base/java.lang=ALL-UNNAMED",
          "--add-opens=java.base/java.util=ALL-UNNAMED",
        )
      }

      project.configureSlimTests()
    }
    project.extensions.findByType<ApplicationExtension>()?.run { lint.configureLint(project) }
    project.extensions.findByType<LibraryExtension>()?.run { lint.configureLint(project) }
  }
}

private fun Lint.configureLint(project: Project) {
  abortOnError = false
  checkReleaseBuilds = false
  warningsAsErrors = false
  baseline = project.file("lint-baseline.xml")
}
