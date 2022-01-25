package dev.msfjarvis.aps.ssh.generator

import java.security.KeyPair

interface SSHKeyGenerator {
  suspend fun generateKey(requiresAuthentication: Boolean): KeyPair
}
