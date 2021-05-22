package dev.msfjarvis.aps.injection.context

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class ContextModule {

  /**
   * A method which provides the path of app-specific files directory. This is useful where we want
   * to perform file operations but we do not want to depend on the [Context]. Injecting this in
   * place of [Context] allows the method/class to be unit-tested without any mocks/fakes.
   *
   * @param context [ApplicationContext]
   * @return the path of app-specific files directory.
   */
  @Provides
  @FilesDirPath
  fun providesFilesDirPath(@ApplicationContext context: Context): String {
    return context.filesDir.path
  }
}
