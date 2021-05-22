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

  @Provides
  @FilesDirPath
  fun providesFilesDirPath(@ApplicationContext context: Context): String {
    return context.filesDir.path
  }
}
