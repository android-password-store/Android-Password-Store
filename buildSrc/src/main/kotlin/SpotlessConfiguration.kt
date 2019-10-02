import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

val kotlinLicenseHeader = """/*
 * Copyright © 2014-2019 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0   
 */
""".trimIndent()

fun Project.configureSpotless() {
    apply<SpotlessPlugin>()

    configure<SpotlessExtension> {
        java {
            target("**/src/main/**/*.java")
            trimTrailingWhitespace()
            // @Suppress("INACCESSIBLE_TYPE")
            // licenseHeader(kotlinLicenseHeader)
            removeUnusedImports()
            googleJavaFormat().aosp()
            endWithNewline()
        }

        kotlinGradle {
            target("*.gradle.kts", "gradle/*.gradle.kts", "buildSrc/*.gradle.kts")
            ktlint("0.31.0").userData(mapOf("indent_size" to "4", "continuation_indent_size" to "4"))
            // @Suppress("INACCESSIBLE_TYPE")
            // licenseHeader(kotlinLicenseHeader, "import|tasks|apply|plugins|include")
            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
        }

        kotlin {
            target("**/src/main/**/*.kt", "buildSrc/**/*.kt")
            ktlint("0.31.0").userData(mapOf("indent_size" to "4", "continuation_indent_size" to "4"))
            // @Suppress("INACCESSIBLE_TYPE")
            // licenseHeader(kotlinLicenseHeader)
            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
        }
    }
}
