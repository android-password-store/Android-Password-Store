package dev.msfjarvis.aps.injection.context

import android.content.Context
import dev.msfjarvis.aps.util.settings.GitSettings
import javax.inject.Qualifier

/**
 * Qualifier for a string value that needs to be provided to the [GitSettings]. It points to
 * `applicationContext.filesDir` and helps in removing the dependency on [Context].
 */
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class FilesDirPath
