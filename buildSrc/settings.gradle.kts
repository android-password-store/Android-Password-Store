/**
 * IDEs don't support this very well for buildSrc, so we use the regular dependency format
 * until that changes.
dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}
*/
