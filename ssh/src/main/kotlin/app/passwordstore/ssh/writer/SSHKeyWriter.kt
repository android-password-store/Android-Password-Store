package app.passwordstore.ssh.writer

import app.passwordstore.ssh.SSHKey
import java.security.KeyPair

public interface SSHKeyWriter {

  public suspend fun writeKeyPair(keyPair: KeyPair, sshKeyFile: SSHKey)
}
