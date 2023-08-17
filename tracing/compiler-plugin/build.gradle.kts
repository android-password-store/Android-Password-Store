plugins {
  id("com.github.android-password-store.kotlin-jvm-library")
  alias(libs.plugins.ksp)
}

dependencies {
  compileOnly(libs.build.kotlin.compiler)
  ksp(libs.build.auto.ksp)
  compileOnly(libs.build.auto.annotations)
  compileOnly(projects.tracing.runtime)
  testImplementation(projects.tracing.runtime)
  testImplementation(libs.bundles.testDependencies)
  testImplementation(libs.build.kct)
}
