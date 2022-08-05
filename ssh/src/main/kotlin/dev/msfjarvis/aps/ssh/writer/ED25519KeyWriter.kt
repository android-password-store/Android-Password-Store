package dev.msfjarvis.aps.ssh.writer

import android.content.Context
import dev.msfjarvis.aps.ssh.SSHKey
import dev.msfjarvis.aps.ssh.utils.SSHKeyUtils.getOrCreateWrappedPrivateKeyFile
import dev.msfjarvis.aps.ssh.utils.createStringPublicKey
import java.io.File
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.i2p.crypto.eddsa.EdDSAPrivateKey

public class ED25519KeyWriter(
  private val context: Context,
  private val requiresAuthentication: Boolean,
) : SSHKeyWriter {

  override suspend fun writeKeyPair(keyPair: KeyPair, sshKeyFile: SSHKey) {
    writePrivateKey(keyPair.private, sshKeyFile.privateKey)
    writePublicKey(keyPair.public, sshKeyFile.publicKey)
  }

  private suspend fun writePrivateKey(privateKey: PrivateKey, privateKeyFile: File) {
    withContext(Dispatchers.IO) {
      val encryptedPrivateKeyFile =
        getOrCreateWrappedPrivateKeyFile(context, requiresAuthentication, privateKeyFile)
      encryptedPrivateKeyFile.openFileOutput().use { os ->
        os.write((privateKey as EdDSAPrivateKey).seed)
      }
    }
  }

  private suspend fun writePublicKey(publicKey: PublicKey, publicKeyFile: File) {
    publicKeyFile.writeText(publicKey.createStringPublicKey())
  }
}
