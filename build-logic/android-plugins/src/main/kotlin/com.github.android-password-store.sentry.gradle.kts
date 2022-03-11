@file:Suppress("PropertyName")

import flavors.FlavorDimensions
import flavors.ProductFlavors
import io.sentry.android.gradle.InstrumentationFeature

plugins {
  id("com.android.application")
  id("io.sentry.android.gradle")
}

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

sentry {
  autoUploadProguardMapping.set(true)
  ignoredBuildTypes.set(setOf("debug"))
  ignoredFlavors.set(setOf(ProductFlavors.FREE))
  tracingInstrumentation {
    enabled.set(true)
    features.set(setOf(InstrumentationFeature.FILE_IO))
  }
}
