package dev.msfjarvis.aps.crypto

public sealed class CryptoException(message: String? = null) : Exception(message)

/** Sealed exception types for [KeyManager]. */
public sealed class KeyManagerException(message: String? = null) : CryptoException(message) {

  /** Store contains no keys. */
  public object NoKeysAvailableException : KeyManagerException("No keys were found")

  /** Key directory does not exist or cannot be accessed. */
  public object KeyDirectoryUnavailableException :
    KeyManagerException("Key directory does not exist")

  /** Failed to delete given key. */
  public object KeyDeletionFailedException : KeyManagerException("Couldn't delete the key file")

  /** Failed to parse the key as a known type. */
  public object InvalidKeyException :
    KeyManagerException("Given key cannot be parsed as a known key type")

  /** No key matching [keyId] could be found. */
  public class KeyNotFoundException(keyId: String) :
    KeyManagerException("No key found with id: $keyId")

  /** Attempting to add another key for [keyId] without requesting a replace. */
  public class KeyAlreadyExistsException(keyId: String) :
    KeyManagerException("Pre-existing key was found for $keyId")
}
