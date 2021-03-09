/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import java.util.Properties
import org.gradle.api.Project

private const val KEYSTORE_CONFIG_PATH = "keystore.properties"

/** Configure signing for all build types. */
@Suppress("UnstableApiUsage")
internal fun BaseAppModuleExtension.configureBuildSigning(project: Project) {
  with(project) {
    val keystoreConfigFile = rootProject.layout.projectDirectory.file(KEYSTORE_CONFIG_PATH)
    if (!keystoreConfigFile.asFile.exists()) return
    val contents = providers.fileContents(keystoreConfigFile).asText.forUseAtConfigurationTime()
    val keystoreProperties = Properties()
    keystoreProperties.load(contents.get().byteInputStream())
    signingConfigs {
      register("release") {
        keyAlias = keystoreProperties["keyAlias"] as String
        keyPassword = keystoreProperties["keyPassword"] as String
        storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
        storePassword = keystoreProperties["storePassword"] as String
      }
    }
    val signingConfig = signingConfigs.getByName("release")
    buildTypes.all { setSigningConfig(signingConfig) }
  }
}
