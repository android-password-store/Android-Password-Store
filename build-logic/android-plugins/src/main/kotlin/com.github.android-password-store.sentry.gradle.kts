@file:Suppress("PropertyName")

import flavors.FlavorDimensions
import flavors.ProductFlavors

plugins { id("com.android.application") }

val SENTRY_DSN_PROPERTY = "SENTRY_DSN"
val INVOKED_FROM_IDE_PROPERTY = "android.injected.invoked.from.ide"

android {
  androidComponents {
    onVariants(selector().withFlavor(FlavorDimensions.FREE to ProductFlavors.NON_FREE)) { variant ->
      val sentryDsn = project.providers.environmentVariable(SENTRY_DSN_PROPERTY)
      if (sentryDsn.isPresent) {
        variant.manifestPlaceholders.put("sentryDsn", sentryDsn.get())
      } else if (project.providers.gradleProperty(INVOKED_FROM_IDE_PROPERTY).orNull != "true") {
        // Checking for 'INVOKED_FROM_IDE_PROPERTY' prevents failures during Gradle sync by the IDE
        throw GradleException(
          "The '${SENTRY_DSN_PROPERTY}' environment variable must be set when building the ${ProductFlavors.NON_FREE} flavor"
        )
      }
    }
  }
}
