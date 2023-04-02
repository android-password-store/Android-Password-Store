package app.passwordstore.ssh.writer

import app.passwordstore.ssh.SSHKey
import java.security.KeyPair

public class ImportedKeyWriter(private val privateKey: String) : SSHKeyWriter {

  override suspend fun writeKeyPair(keyPair: KeyPair, sshKeyFile: SSHKey) {
    // Write the string key instead of the key from the key pair
    sshKeyFile.privateKey.writeText(privateKey)
  }
}
