package dev.msfjarvis.aps.crypto

public sealed class CryptoException(message: String? = null) : Exception(message)

public sealed class KeyPairException(message: String? = null) : CryptoException(message) {
  public object PrivateKeyUnavailableException :
    KeyPairException("Key object does not have a private sub-key")
}

public sealed class KeyManagerException(message: String? = null) : CryptoException(message) {
  public object NoKeysAvailableException : KeyManagerException("No keys were found")
  public object KeyDirectoryUnavailableException :
    KeyManagerException("Key directory does not exist")
  public object KeyDeletionFailedException : KeyManagerException("Couldn't delete the key file")
  public class KeyNotFoundException(keyId: String) :
    KeyManagerException("No key found with id: $keyId")
  public class KeyAlreadyExistsException(keyId: String) :
    KeyManagerException("Pre-existing key was found for $keyId but 'replace' is set to false")
}
