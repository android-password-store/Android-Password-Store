package app.passwordstore.injection.ssh

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.msfjarvis.aps.ssh.SSHKeyManager

@Module
@InstallIn(SingletonComponent::class)
object SSHKeyManagerModule {

  @Provides
  @Reusable
  // TODO: verify that SSHKeyManager is stateless else use Singleton
  fun provideSSHKeyManager(@ApplicationContext context: Context): SSHKeyManager {
    return SSHKeyManager(context)
  }
}