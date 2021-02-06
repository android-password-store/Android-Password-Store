plugins {
    id("com.android.library")
    id("com.vanniktech.maven.publish")
    kotlin("android")
    `aps-plugin`
}

android {
    defaultConfig {
        versionCode = 2
        versionName = "2.0"
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

dependencies {
    compileOnly(Dependencies.AndroidX.annotation)
    implementation(Dependencies.AndroidX.autofill)
    implementation(Dependencies.Kotlin.Coroutines.android)
    implementation(Dependencies.Kotlin.Coroutines.core)
    implementation(Dependencies.ThirdParty.timberkt)
}
