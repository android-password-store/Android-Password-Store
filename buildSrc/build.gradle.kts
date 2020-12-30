apply(from = "buildDependencies.gradle")
val build: Map<Any, Any> by extra

plugins {
    `kotlin-dsl`
}

repositories {
    google()
    gradlePluginPortal()
    jcenter()
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
    }
}

dependencies {
    implementation(build.getValue("kotlinGradlePlugin"))
    implementation(build.getValue("androidGradlePlugin"))
    implementation(build.getValue("binaryCompatibilityValidator"))
    implementation(build.getValue("downloadTaskPlugin"))
}
