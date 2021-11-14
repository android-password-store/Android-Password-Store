plugins { `kotlin-dsl` }

dependencies {
  implementation(libs.build.binarycompat)
  implementation(libs.build.kotlin)
  implementation(libs.build.spotless)
}
