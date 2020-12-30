/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    kotlin("android")
    `aps-plugin`
    `crowdin-plugin`
}

configure<CrowdinExtension> {
    projectName = "android-password-store"
}

android {
    if (isSnapshot()) {
        applicationVariants.all {
            outputs.all {
                (this as BaseVariantOutputImpl).outputFileName = "aps-${flavorName}_$versionName.apk"
            }
        }
    }

    defaultConfig {
        applicationId = "dev.msfjarvis.aps"
        versionCode = 2_00_00
        versionName = "2.0.0-SNAPSHOT"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lintOptions {
        isAbortOnError = true
        isCheckReleaseBuilds = false
        disable("MissingTranslation", "PluralsCandidate", "ImpliedQuantity")
    }

    flavorDimensions("free")
    productFlavors {
        create("free") {
        }
        create("nonFree") {
        }
    }
}

dependencies {
    compileOnly(Dependencies.AndroidX.annotation)
    implementation(project(":autofill-parser"))
    implementation(project(":openpgp-ktx"))
    implementation(Dependencies.AndroidX.activity_ktx)
    implementation(Dependencies.AndroidX.appcompat)
    implementation(Dependencies.AndroidX.autofill)
    implementation(Dependencies.AndroidX.biometric_ktx)
    implementation(Dependencies.AndroidX.constraint_layout)
    implementation(Dependencies.AndroidX.core_ktx)
    implementation(Dependencies.AndroidX.documentfile)
    implementation(Dependencies.AndroidX.fragment_ktx)
    implementation(Dependencies.AndroidX.lifecycle_common)
    implementation(Dependencies.AndroidX.lifecycle_livedata_ktx)
    implementation(Dependencies.AndroidX.lifecycle_viewmodel_ktx)
    implementation(Dependencies.AndroidX.material)
    implementation(Dependencies.AndroidX.preference)
    implementation(Dependencies.AndroidX.recycler_view)
    implementation(Dependencies.AndroidX.recycler_view_selection)
    implementation(Dependencies.AndroidX.security)
    implementation(Dependencies.AndroidX.swiperefreshlayout)

    implementation(Dependencies.Kotlin.Coroutines.android)
    implementation(Dependencies.Kotlin.Coroutines.core)

    implementation(Dependencies.FirstParty.zxing_android_embedded)

    implementation(Dependencies.ThirdParty.commons_codec)
    implementation(Dependencies.ThirdParty.eddsa)
    implementation(Dependencies.ThirdParty.fastscroll)
    implementation(Dependencies.ThirdParty.jgit) {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation(Dependencies.ThirdParty.kotlin_result)
    implementation(Dependencies.ThirdParty.sshj)
    implementation(Dependencies.ThirdParty.bouncycastle)
    implementation(Dependencies.ThirdParty.plumber)
    implementation(Dependencies.ThirdParty.ssh_auth)
    implementation(Dependencies.ThirdParty.timber)
    implementation(Dependencies.ThirdParty.timberkt)

    if (isSnapshot()) {
        implementation(Dependencies.ThirdParty.leakcanary)
        implementation(Dependencies.ThirdParty.whatthestack)
    } else {
        debugImplementation(Dependencies.ThirdParty.leakcanary)
        debugImplementation(Dependencies.ThirdParty.whatthestack)
    }

    "nonFreeImplementation"(Dependencies.NonFree.google_play_auth_api_phone)

    // Testing-only dependencies
    androidTestImplementation(Dependencies.Testing.junit)
    androidTestImplementation(Dependencies.Testing.kotlin_test_junit)
    androidTestImplementation(Dependencies.Testing.AndroidX.runner)
    androidTestImplementation(Dependencies.Testing.AndroidX.rules)

    testImplementation(Dependencies.Testing.junit)
    testImplementation(Dependencies.Testing.kotlin_test_junit)
}
