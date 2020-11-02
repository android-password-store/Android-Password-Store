plugins {
    id("com.android.library")
    id("maven-publish")
    kotlin("android")
    `aps-plugin`
}

// Type safety can sometimes suck
fun getCredential(type: String): String {
    return when (type) {
        // Attempt to find credentials passed by -Pmaven.$type=
        "user", "password" -> (findProperty("maven.$type")
            // Fall back to MAVEN_$type from env
            ?: System.getenv("MAVEN_${type.toUpperCase()}"))?.toString()
            // Finally fallthrough to an empty string to let task configuration complete
            // even if actual publishing is going to fail
            ?: ""
        else -> throw IllegalArgumentException("Invalid credential type: $type")
    }
}

android {
    defaultConfig {
        versionCode = 1
        versionName = "1.0"
        consumerProguardFiles("consumer-rules.pro")
    }

    kotlin {
        explicitApi()
    }

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xexplicit-api=strict"
        )
    }
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "aps"
                url = uri("https://maven.msfjarvis.dev/android-password-store/${findProperty("POM_ARTIFACT_ID")}")
                credentials {
                    username = getCredential("user")
                    password = getCredential("password")
                }
            }
        }
        publications {
            create<MavenPublication>("apsMaven") {
                fun getKey(propertyName: String): String {
                    return findProperty(propertyName)?.toString() ?: error("Failed to find property for $propertyName")
                }

                from(components.getByName("release"))
                groupId = getKey("GROUP")
                artifactId = getKey("POM_ARTIFACT_ID")
                version = getKey("VERSION_NAME")
                pom {
                    name.set(getKey("POM_ARTIFACT_ID"))
                    description.set(getKey("POM_ARTIFACT_DESCRIPTION"))
                    url.set(getKey("POM_URL"))
                    licenses {
                        license {
                            name.set(getKey("POM_LICENSE_NAME"))
                            url.set(getKey("POM_LICENSE_URL"))
                        }
                    }
                    developers {
                        developer {
                            id.set(getKey("POM_DEVELOPER_ID"))
                            name.set(getKey("POM_DEVELOPER_NAME"))
                            email.set(getKey("POM_DEVELOPER_EMAIL"))
                        }
                    }
                    scm {
                        connection.set(getKey("POM_SCM_CONNECTION"))
                        developerConnection.set(getKey("POM_SCM_DEV_CONNECTION"))
                        url.set(getKey("POM_SCM_URL"))
                    }
                }
            }
        }
    }
}

dependencies {
    compileOnly(Dependencies.AndroidX.annotation)
    implementation(Dependencies.AndroidX.autofill)
    implementation(Dependencies.Kotlin.Coroutines.android)
    implementation(Dependencies.Kotlin.Coroutines.core)
    implementation(Dependencies.ThirdParty.timberkt)
}
