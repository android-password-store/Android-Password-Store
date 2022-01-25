package dev.msfjarvis.aps.ssh.generator

import java.security.KeyPair

interface SSHKeyGenerator {
  fun generateKey(requiresAuthentication: Boolean): KeyPair

  companion object {
    internal const val PROVIDER_ANDROID_KEY_STORE = "AndroidKeyStore"
    internal const val KEYSTORE_ALIAS = "sshkey"
    internal const val ANDROIDX_SECURITY_KEYSET_PREF_NAME = "androidx_sshkey_keyset_prefs"
  }
}