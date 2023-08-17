plugins {
  `java-gradle-plugin`
  id("com.github.android-password-store.kotlin-jvm-library")
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.ksp)
}

rootProject.tasks.named("ktfmtCheck").configure { dependsOn(tasks.named("generateBuildConfig")) }

buildConfig {
  val project = projects.tracing.compilerPlugin
  packageName("dev.msfjarvis.tracing")
  useKotlinOutput { internalVisibility = true }
  buildConfigField("String", "KOTLIN_PLUGIN_GROUP", "\"${project.group}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_NAME", "\"${project.name}\"")
  buildConfigField("String", "KOTLIN_PLUGIN_VERSION", "\"${project.version}\"")
}

dependencies {
  implementation(libs.build.kotlin)
  ksp(libs.build.auto.ksp)
  compileOnly(libs.build.auto.annotations)
}
