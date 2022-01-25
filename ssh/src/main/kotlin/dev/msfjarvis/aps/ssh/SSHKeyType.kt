package dev.msfjarvis.aps.ssh

enum class SSHKeyType(val value: String) {
  Imported("imported"),
  KeystoreNative("keystore_native"),
  KeystoreWrappedEd25519("keystore_wrapped_ed25519"),

  // Behaves like `Imported`, but allows to view the public key.
  LegacyGenerated("legacy_generated"),
  ;

  companion object {

    fun fromValue(type: String?): SSHKeyType? = values().associateBy { it.value }[type]
  }
}
