plugins { `kotlin-dsl` }

repositories {
  mavenCentral()
  google()
  gradlePluginPortal()
}

kotlinDslPluginOptions { experimentalWarning.set(false) }

// force compilation of Dependencies.kt so it can be referenced in buildSrc/build.gradle.kts
sourceSets.main {
  java {
    setSrcDirs(setOf(projectDir.parentFile.resolve("src/main/java")))
    include("Dependencies.kt")
  }
}
