plugins {
    `kotlin-dsl`
}

repositories {
    google()
    gradlePluginPortal()
    jcenter()
    // For binary compatibility validator.
    maven { url = uri("https://kotlin.bintray.com/kotlinx") }
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

gradlePlugin {
    plugins {
        register("aps") {
            id = "aps-plugin"
            implementationClass = "PasswordStorePlugin"
        }
        register("crowdin") {
            id = "crowdin-plugin"
            implementationClass = "CrowdinDownloadPlugin"
        }
        register("versioning") {
            id = "versioning-plugin"
            implementationClass = "VersioningPlugin"
        }
    }
}

dependencies {
    implementation(Plugins.androidGradlePlugin)
    implementation(Plugins.binaryCompatibilityValidator)
    implementation(Plugins.downloadTaskPlugin)
    implementation(Plugins.jsemver)
    implementation(Plugins.kotlinGradlePlugin)
}
