package dev.msfjarvis.aps.ssh

public enum class SSHKeyType(internal val value: String) {
  Imported("imported"),
  KeystoreNative("keystore_native"),
  KeystoreWrappedEd25519("keystore_wrapped_ed25519"),

  // Behaves like `Imported`, but allows to view the public key.
  LegacyGenerated("legacy_generated"),
  ;

  public companion object {

    public fun fromValue(type: String?): SSHKeyType? = values().associateBy { it.value }[type]
  }
}
