plugins {
  id("com.github.android-password-store.android-library")
  id("com.github.android-password-store.kotlin-android")
  id("com.github.android-password-store.kotlin-library")
}

dependencies {
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.hilt.compose)
  implementation(libs.compose.foundation.core)
  implementation(libs.compose.foundation.layout)
  implementation(libs.compose.material)
  implementation(libs.androidx.compose.material3)
  implementation(libs.compose.ui.core)
  implementation(libs.compose.ui.viewbinding)
  compileOnly(libs.compose.ui.tooling)
}
