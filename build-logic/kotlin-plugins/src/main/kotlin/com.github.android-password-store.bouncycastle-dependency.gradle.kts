/**
 * Bouncy Castle 1.71 changed their packaging to stop shipping jdk15on artifacts, and instead use
 * multi-release JARs with the jdk15to18 suffix. This plugin replaces older dependencies to use the
 * new version and artifact.
 */
configurations.all {
  resolutionStrategy.eachDependency {
    if (requested.group == "org.bouncycastle" && requested.name.contains("jdk15on")) {
      val replacement = "${requested.group}:${requested.name.replace("jdk15on", "jdk15to18")}:1.71"
      useTarget(replacement)
    }
  }
}
