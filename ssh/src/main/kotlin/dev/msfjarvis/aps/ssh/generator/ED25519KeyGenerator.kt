package dev.msfjarvis.aps.ssh.generator

import java.security.KeyPair
import net.i2p.crypto.eddsa.KeyPairGenerator

class ED25519KeyGenerator : SSHKeyGenerator {

  override suspend fun generateKey(requiresAuthentication: Boolean): KeyPair {
    // Generate the ed25519 key pair and encrypt the private key.
    return KeyPairGenerator().generateKeyPair()
  }
}
