package app.passwordstore.ssh.writer

import app.passwordstore.ssh.SSHKey
import app.passwordstore.ssh.utils.createStringPublicKey
import java.security.KeyPair

public class KeystoreNativeKeyWriter : SSHKeyWriter {

  override suspend fun writeKeyPair(keyPair: KeyPair, sshKeyFile: SSHKey) {
    // Android Keystore manages the private key for us
    // Write public key in SSH format to .ssh_key.pub.
    sshKeyFile.publicKey.writeText(keyPair.public.createStringPublicKey())
  }
}
