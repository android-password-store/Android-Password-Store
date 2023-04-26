package app.passwordstore.gradle

import com.android.build.api.dsl.Lint
import org.gradle.api.Project

object LintConfig {
  fun Lint.configureLint(project: Project) {
    quiet = project.providers.environmentVariable("CI").isPresent
    abortOnError = true
    checkReleaseBuilds = true
    warningsAsErrors = true
    ignoreWarnings = false
    checkAllWarnings = true
    noLines = false
    showAll = true
    explainIssues = true
    textReport = false
    xmlReport = false
    htmlReport = true
    sarifReport = true
    // Noisy, not particularly actionable
    disable += "DuplicateStrings"
    // We deal with dependency upgrades separately
    disable += "NewerVersionAvailable"
    // Noisy
    disable += "SyntheticAccessor"
    // Noisy, not particularly actionable
    disable += "TypographyQuotes"
    // False-positives abound due to use of ViewBinding
    disable += "UnusedIds"
    baseline = project.file("lint-baseline.xml")
  }
}
