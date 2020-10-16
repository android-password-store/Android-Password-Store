/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.plugins.AppPlugin
import com.android.build.gradle.internal.plugins.LibraryPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

class PasswordStorePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.configureForAllProjects()

        if (project.isRoot) {
            project.configureForRootProject()
        }

        project.plugins.all {
            when (this) {
                is JavaPlugin,
                is JavaLibraryPlugin -> {
                    project.tasks.withType<JavaCompile> {
                        options.compilerArgs.add("-Xlint:unchecked")
                        options.isDeprecation = true
                    }
                }
                is LibraryPlugin,
                is AppPlugin -> {
                    project.extensions.getByType<TestedExtension>().configureCommonAndroidOptions()
                }
            }
        }
    }
}

private val Project.isRoot get() = this == this.rootProject
