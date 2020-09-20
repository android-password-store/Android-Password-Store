plugins {
  kotlin("android")
  id("maven-publish")
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
                from(components.getByName("release"))
                groupId = findProperty("GROUP").toString()
                artifactId = findProperty("POM_ARTIFACT_ID").toString()
                version = findProperty("VERSION_NAME").toString()
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
