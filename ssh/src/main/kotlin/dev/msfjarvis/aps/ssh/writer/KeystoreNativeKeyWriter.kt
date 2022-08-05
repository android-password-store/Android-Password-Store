package dev.msfjarvis.aps.ssh.writer

import dev.msfjarvis.aps.ssh.SSHKey
import dev.msfjarvis.aps.ssh.utils.createStringPublicKey
import java.security.KeyPair

public class KeystoreNativeKeyWriter : SSHKeyWriter {

  override suspend fun writeKeyPair(keyPair: KeyPair, sshKeyFile: SSHKey) {
    // Android Keystore manages the private key for us
    // Write public key in SSH format to .ssh_key.pub.
    sshKeyFile.publicKey.writeText(keyPair.public.createStringPublicKey())
  }
}
