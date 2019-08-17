import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

buildscript {
    repositories {
        google()
        jcenter()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.5.0-rc03")
        classpath(kotlin("gradle-plugin", "1.3.41"))
    }
}

plugins {
    id("com.github.ben-manes.versions") version "0.22.0"
}

allprojects {
    repositories {
        google()
        jcenter()
        mavenCentral()
    }
}
tasks {
    named<DependencyUpdatesTask>("dependencyUpdates") {
        resolutionStrategy {
            componentSelection {
                all {
                    if (listOf("commons-io", "org.eclipse.jgit").contains(candidate.group)) {
                        reject("Blacklisted package")
                    }
                }
            }
        }
        checkForGradleUpdate = true
        outputFormatter = "json"
        outputDir = "build/dependencyUpdates"
        reportfileName = "report"
    }

    named<Wrapper>("wrapper") {
        gradleVersion = "5.6"
        distributionType = Wrapper.DistributionType.ALL
    }
}
