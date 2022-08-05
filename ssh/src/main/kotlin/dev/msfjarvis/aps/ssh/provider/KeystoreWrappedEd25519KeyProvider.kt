package dev.msfjarvis.aps.ssh.provider

import android.content.Context
import dev.msfjarvis.aps.ssh.SSHKey
import dev.msfjarvis.aps.ssh.utils.SSHKeyUtils.getOrCreateWrappedPrivateKeyFile
import dev.msfjarvis.aps.ssh.utils.parseStringPublicKey
import java.io.IOException
import java.security.PrivateKey
import java.security.PublicKey
import kotlinx.coroutines.runBlocking
import logcat.asLog
import logcat.logcat
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.KeyProvider

internal class KeystoreWrappedEd25519KeyProvider(
  private val context: Context,
  private val sshKeyFile: SSHKey
) : KeyProvider {

  override fun getPublic(): PublicKey =
    runCatching { sshKeyFile.publicKey.readText().parseStringPublicKey()!! }
      .getOrElse { error ->
        logcat { error.asLog() }
        throw IOException("Failed to get the public key for wrapped ed25519 key", error)
      }

  override fun getPrivate(): PrivateKey =
    runCatching {
        // The current MasterKey API does not allow getting a reference to an existing
        // one
        // without specifying the KeySpec for a new one. However, the value for passed
        // here
        // for `requireAuthentication` is not used as the key already exists at this
        // point.
        val encryptedPrivateKeyFile = runBlocking {
          getOrCreateWrappedPrivateKeyFile(context, false, sshKeyFile.privateKey)
        }
        val rawPrivateKey = encryptedPrivateKeyFile.openFileInput().use { it.readBytes() }
        EdDSAPrivateKey(
          EdDSAPrivateKeySpec(rawPrivateKey, EdDSANamedCurveTable.ED_25519_CURVE_SPEC)
        )
      }
      .getOrElse { error ->
        logcat { error.asLog() }
        throw IOException("Failed to unwrap wrapped ed25519 key", error)
      }

  override fun getType(): KeyType = KeyType.fromKey(public)
}
