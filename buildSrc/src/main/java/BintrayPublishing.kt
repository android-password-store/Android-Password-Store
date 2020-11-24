/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.android.build.gradle.TestedExtension
import java.util.Locale
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/**
 * Register the `sourcesJar` task so that our published artifacts can include them.
 */
internal fun TestedExtension.registerSourcesJarTask(project: Project) {
    project.tasks.register<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets.getByName("main").java.srcDirs)
    }
}

/**
 * Configures the `apsMaven` and `bintray` repositories along with an `aps` publication
 */
internal fun PublishingExtension.configureMavenPublication(project: Project) {
    repositories {
        maven {
            name = "apsMaven"
            url = project.uri("https://maven.msfjarvis.dev/android-password-store/${project.getKey("POM_ARTIFACT_ID")}")
            credentials {
                username = project.getCredential("user")
                password = project.getCredential("password")
            }
        }
        maven {
            val artifactId = project.getKey("POM_ARTIFACT_ID")
            name = "bintray"
            url = project.uri("https://api.bintray.com/maven/android-password-store/$artifactId/$artifactId/;publish=1;override=0")
            credentials {
                username = project.getCredential("user")
                password = project.getCredential("password")
            }
        }
    }
    publications {
        create<MavenPublication>("aps") {
            from(project.components.getByName("release"))
            groupId = project.getKey("GROUP")
            artifactId = project.getKey("POM_ARTIFACT_ID")
            version = project.getKey("VERSION_NAME")
            artifact(project.tasks.getByName("sourcesJar"))
            configurePom(project)
        }
    }
}

private fun Project.getCredential(type: String): String {
    return when (type) {
        // Attempt to find credentials passed by -Pmaven.$type=
        "user", "password" -> (findProperty("maven.$type")
        // Fall back to MAVEN_$type from env
            ?: System.getenv("MAVEN_${type.toUpperCase(Locale.ROOT)}"))?.toString()
        // Finally fallthrough to an empty string to let task configuration complete
        // even if actual publishing is going to fail
            ?: ""
        else -> throw IllegalArgumentException("Invalid credential type: $type")
    }
}

private fun Project.getKey(propertyName: String): String {
    return findProperty(propertyName)?.toString()
        ?: error("Failed to find value for property: $propertyName")
}

private fun MavenPublication.configurePom(project: Project) {
    pom {
        name.set(project.getKey("POM_ARTIFACT_ID"))
        description.set(project.getKey("POM_ARTIFACT_DESCRIPTION"))
        url.set(project.getKey("POM_URL"))
        licenses {
            license {
                name.set(project.getKey("POM_LICENSE_NAME"))
                url.set(project.getKey("POM_LICENSE_URL"))
            }
        }
        developers {
            developer {
                id.set(project.getKey("POM_DEVELOPER_ID"))
                name.set(project.getKey("POM_DEVELOPER_NAME"))
                email.set(project.getKey("POM_DEVELOPER_EMAIL"))
            }
        }
        scm {
            connection.set(project.getKey("POM_SCM_CONNECTION"))
            developerConnection.set(project.getKey("POM_SCM_DEV_CONNECTION"))
            url.set(project.getKey("POM_SCM_URL"))
        }
    }
}
