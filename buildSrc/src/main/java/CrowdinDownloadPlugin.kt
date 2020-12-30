/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import de.undercouch.gradle.tasks.download.Download
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

class CrowdinDownloadPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        with(project) {
            val extension = extensions.create<CrowdinExtension>("crowdin")
            afterEvaluate {
                val projectName = extension.projectName
                if (projectName.isEmpty()) {
                    throw GradleException(
                        """
                        Applying `crowdin-plugin` requires a projectName to be configured via the "crowdin" extension.
                        """.trimIndent()
                    )
                }
                tasks.register<Download>("downloadCrowdin") {
                    src("https://crowdin.com/backend/download/project/$projectName.zip")
                    dest("$buildDir/translations.zip")
                    overwrite(true)
                }
                tasks.register<Copy>("extractCrowdin") {
                    setDependsOn(setOf("downloadCrowdin"))
                    doFirst {
                        File(buildDir, "translations").deleteRecursively()
                    }
                    from(zipTree("$buildDir/translations.zip"))
                    into("$buildDir/translations")
                }

                tasks.register<Copy>("crowdin") {
                    setDependsOn(setOf("extractCrowdin"))
                    from("$buildDir/translations/${project.name}/src/main/res")
                    into("${projectDir}/src/main/res")
                    doLast {
                        File("${buildDir}/translations").deleteRecursively()
                        File("${buildDir}/translations.zip").delete()
                    }
                }
            }
        }
    }
}
