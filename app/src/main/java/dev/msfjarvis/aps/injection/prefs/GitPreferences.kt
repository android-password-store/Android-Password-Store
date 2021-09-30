package dev.msfjarvis.aps.injection.prefs

import android.content.SharedPreferences
import javax.inject.Qualifier

/**
 * Qualifies a [SharedPreferences] instance specifically used for encrypted Git-related settings.
 */
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class GitPreferences
