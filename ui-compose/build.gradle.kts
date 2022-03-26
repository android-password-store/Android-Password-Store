plugins {
  id("com.github.android-password-store.android-library")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-library")
}

android {
  buildFeatures {
    compose = true
    composeOptions {
      useLiveLiterals = false
      kotlinCompilerExtensionVersion = libs.versions.compose.get()
    }
  }
}

dependencies {
  api(libs.compose.foundation.core)
  api(libs.compose.foundation.layout)
  api(libs.compose.material)
  api(libs.compose.material3)
  api(libs.compose.ui.core)
}
