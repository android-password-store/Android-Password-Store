package dev.msfjarvis.aps.ssh.generator

import java.security.KeyPair

interface SSHKeyGenerator {
  fun generateKey(requiresAuthentication: Boolean): KeyPair

  companion object {
  }
}