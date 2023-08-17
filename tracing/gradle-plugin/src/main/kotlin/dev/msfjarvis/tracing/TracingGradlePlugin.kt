package dev.msfjarvis.tracing

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class TracingGradlePlugin : KotlinCompilerPluginSupportPlugin {

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    return kotlinCompilation.target.project.provider { emptyList() }
  }

  override fun getCompilerPluginId(): String = "dev.msfjarvis.tracing"

  override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact(
      groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
      artifactId = BuildConfig.KOTLIN_PLUGIN_NAME,
      version = BuildConfig.KOTLIN_PLUGIN_VERSION
    )

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true
}
