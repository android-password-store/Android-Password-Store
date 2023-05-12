package app.passwordstore.gradle

import app.passwordstore.gradle.LintConfig.configureLint
import app.passwordstore.gradle.flavors.configureSlimTests
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.TestedExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

object AndroidCommon {
  fun configure(project: Project) {
    project.extensions.configure<TestedExtension> {
      setCompileSdkVersion(33)
      defaultConfig {
        minSdk = 26
        targetSdk = 33
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

      project.tasks.withType<Test>().configureEach {
        jvmArgs(
          "--add-opens=java.base/java.lang=ALL-UNNAMED",
          "--add-opens=java.base/java.util=ALL-UNNAMED",
        )
      }

      project.configureSlimTests()
    }
    project.extensions.findByType<ApplicationExtension>()?.run { lint.configureLint(project) }
    project.extensions.findByType<LibraryExtension>()?.run { lint.configureLint(project) }
    val catalog = project.extensions.getByType<VersionCatalogsExtension>()
    val libs = catalog.named("libs")
    project.dependencies.addProvider(
      "lintChecks",
      libs.findLibrary("thirdparty-compose-lints").get()
    )
  }
}
