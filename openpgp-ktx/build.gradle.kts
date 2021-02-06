plugins {
    id("com.android.library")
    id("com.vanniktech.maven.publish")
    kotlin("android")
    `aps-plugin`
}

android {
    defaultConfig {
        consumerProguardFiles("consumer-proguard-rules.pro")
    }

    buildFeatures.aidl = true

    kotlin {
        explicitApi()
    }

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xexplicit-api=strict"
        )
    }
}

dependencies {
    implementation(Dependencies.Kotlin.Coroutines.core)
}
