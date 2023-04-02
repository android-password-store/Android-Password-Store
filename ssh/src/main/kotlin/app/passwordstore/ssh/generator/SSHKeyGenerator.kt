package app.passwordstore.ssh.generator

import java.security.KeyPair

public interface SSHKeyGenerator {
  public suspend fun generateKey(requiresAuthentication: Boolean): KeyPair

  public companion object {
    public const val USER_AUTHENTICATION_TIMEOUT: Int = 30
  }
}
