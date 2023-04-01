package app.passwordstore.injection.ssh

import android.content.Context
import app.passwordstore.ssh.SSHKeyManager
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SSHKeyManagerModule {

  @Provides
  @Reusable
  fun provideSSHKeyManager(@ApplicationContext context: Context): SSHKeyManager {
    return SSHKeyManager(context)
  }
}
