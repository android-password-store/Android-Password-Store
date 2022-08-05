package dev.msfjarvis.aps.ssh.writer

import dev.msfjarvis.aps.ssh.SSHKey
import java.security.KeyPair

public interface SSHKeyWriter {

  public suspend fun writeKeyPair(keyPair: KeyPair, sshKeyFile: SSHKey)
}
