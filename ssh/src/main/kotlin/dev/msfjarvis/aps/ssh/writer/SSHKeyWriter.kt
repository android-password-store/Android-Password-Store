package dev.msfjarvis.aps.ssh.writer

import java.io.File
import java.security.PrivateKey
import java.security.PublicKey

public interface SSHKeyWriter {

  public suspend fun writePrivateKey(privateKey: PrivateKey, privateKeyFile: File)
  public suspend fun writePublicKey(publicKey: PublicKey, publicKeyFile: File)
}
