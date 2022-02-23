import artifacts.CollectApksTask
import artifacts.CollectBundleTask
import com.android.build.api.artifact.SingleArtifact

plugins { id("com.android.application") }

androidComponents {
  onVariants { variant ->
    project.tasks.register<CollectApksTask>("collect${variant.name.capitalize()}Apks") {
      variantName.set(variant.name)
      apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
      builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
      outputDirectory.set(project.layout.projectDirectory.dir("outputs"))
    }
    project.tasks.register<CollectBundleTask>("collect${variant.name.capitalize()}Bundle") {
      variantName.set(variant.name)
      versionName.set(android.defaultConfig.versionName)
      bundleFile.set(variant.artifacts.get(SingleArtifact.BUNDLE))
      outputDirectory.set(project.layout.projectDirectory.dir("outputs"))
    }
  }
}
