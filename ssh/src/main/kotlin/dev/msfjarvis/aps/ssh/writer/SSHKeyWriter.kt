package dev.msfjarvis.aps.ssh.writer

import java.io.File
import java.security.PrivateKey
import java.security.PublicKey

interface SSHKeyWriter {

  suspend fun writePrivateKey(privateKey: PrivateKey, privateKeyFile: File)
  suspend fun writePublicKey(publicKey: PublicKey, publicKeyFile: File)
}
