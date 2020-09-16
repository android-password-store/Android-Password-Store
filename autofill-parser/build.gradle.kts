plugins {
  kotlin("android")
}

android {
    defaultConfig {
        versionCode = 1
        versionName = "1.0"
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(Dependencies.AndroidX.core_ktx)
    implementation(Dependencies.AndroidX.autofill)
    implementation(Dependencies.Kotlin.Coroutines.android)
    implementation(Dependencies.Kotlin.Coroutines.core)
    implementation(Dependencies.ThirdParty.timberkt)
}
