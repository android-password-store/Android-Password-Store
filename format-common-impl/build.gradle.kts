plugins {
  id("com.github.android-password-store.android-library")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-library")
}

android { namespace = "app.passwordstore.format.common.impl" }

dependencies {
  api(projects.formatCommon)
  implementation(libs.dagger.hilt.core)
}
