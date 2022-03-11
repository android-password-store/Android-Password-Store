@file:Suppress("PropertyName")

import flavors.FlavorDimensions
import flavors.ProductFlavors

plugins { id("com.android.application") }

val SENTRY_DSN_PROPERTY = "SENTRY_DSN"

android {
  androidComponents {
    onVariants(selector().withFlavor(FlavorDimensions.FREE to ProductFlavors.NON_FREE)) { variant ->
      val sentryDsn = project.providers.environmentVariable(SENTRY_DSN_PROPERTY)
      if (sentryDsn.isPresent) {
        variant.manifestPlaceholders.put("sentryDsn", sentryDsn.get())
      }
    }
  }
}
