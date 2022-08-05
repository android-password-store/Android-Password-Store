package dev.msfjarvis.aps.ssh.provider

import dev.msfjarvis.aps.ssh.utils.Constants.KEYSTORE_ALIAS
import dev.msfjarvis.aps.ssh.utils.sshPrivateKey
import dev.msfjarvis.aps.ssh.utils.sshPublicKey
import java.io.IOException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import logcat.asLog
import logcat.logcat
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.KeyProvider

internal class KeystoreNativeKeyProvider(private val androidKeystore: KeyStore) : KeyProvider {

  override fun getPublic(): PublicKey =
    runCatching { androidKeystore.sshPublicKey!! }
      .getOrElse { error ->
        logcat { error.asLog() }
        throw IOException("Failed to get public key '$KEYSTORE_ALIAS' from Android Keystore", error)
      }

  override fun getPrivate(): PrivateKey =
    runCatching { androidKeystore.sshPrivateKey!! }
      .getOrElse { error ->
        logcat { error.asLog() }
        throw IOException(
          "Failed to access private key '$KEYSTORE_ALIAS' from Android Keystore",
          error
        )
      }

  override fun getType(): KeyType = KeyType.fromKey(public)
}
