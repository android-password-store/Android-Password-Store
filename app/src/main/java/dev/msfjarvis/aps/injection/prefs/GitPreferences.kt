package dev.msfjarvis.aps.injection.prefs

import android.content.SharedPreferences
import dev.msfjarvis.aps.util.settings.GitSettings
import javax.inject.Qualifier

/**
 * Qualifier for a [SharedPreferences] that needs to be provided to the [GitSettings]. It provides a
 * [SharedPreferences] with the file name "git_operation" which stores the git settings.
 */
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class GitPreferences
