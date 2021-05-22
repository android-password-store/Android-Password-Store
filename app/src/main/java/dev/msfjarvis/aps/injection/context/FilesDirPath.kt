package dev.msfjarvis.aps.injection.context

import android.content.Context
import javax.inject.Qualifier

/** Qualifies a [String] representing the absolute path of [Context.getFilesDir]. */
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class FilesDirPath
