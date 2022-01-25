package dev.msfjarvis.aps.ssh.writer

import dev.msfjarvis.aps.ssh.utils.createStringPublicKey
import java.io.File
import java.security.PrivateKey
import java.security.PublicKey

class ECDSAKeyWriter : SSHKeyWriter {

  override suspend fun writePrivateKey(privateKey: PrivateKey, privateKeyFile: File) {
    // Android Keystore manages this key for us
  }

  override suspend fun writePublicKey(publicKey: PublicKey, publicKeyFile: File) {
    // Write public key in SSH format to .ssh_key.pub.
    publicKeyFile.writeText(publicKey.createStringPublicKey())
  }
}
