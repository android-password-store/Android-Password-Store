package dev.msfjarvis.aps.ssh.generator

import java.security.KeyPair

public interface SSHKeyGenerator {
  public suspend fun generateKey(requiresAuthentication: Boolean): KeyPair
}
