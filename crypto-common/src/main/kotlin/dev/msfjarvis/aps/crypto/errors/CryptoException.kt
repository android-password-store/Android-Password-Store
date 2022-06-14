package dev.msfjarvis.aps.crypto.errors

import dev.msfjarvis.aps.crypto.KeyManager

public sealed class CryptoException(message: String? = null, cause: Throwable? = null) :
  Exception(message, cause)

/** Sealed exception types for [KeyManager]. */
public sealed class KeyManagerException(message: String? = null) : CryptoException(message)

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

/** No key matching `keyId` could be found. */
public class KeyNotFoundException(keyId: String) :
  KeyManagerException("No key found with id: $keyId")

/** Attempting to add another key for `keyId` without requesting a replace. */
public class KeyAlreadyExistsException(keyId: String) :
  KeyManagerException("Pre-existing key was found for $keyId")

/** Sealed exception types for [dev.msfjarvis.aps.crypto.CryptoHandler]. */
public sealed class CryptoHandlerException(message: String? = null, cause: Throwable? = null) :
  CryptoException(message, cause)

/** The passphrase provided for decryption was incorrect. */
public class IncorrectPassphraseException(cause: Throwable) : CryptoHandlerException(null, cause)

/** No keys were provided for encryption. */
public class NoKeysProvided(message: String?) : CryptoHandlerException(message, null)

/** An unexpected error that cannot be mapped to a known type. */
public class UnknownError(cause: Throwable) : CryptoHandlerException(null, cause)
