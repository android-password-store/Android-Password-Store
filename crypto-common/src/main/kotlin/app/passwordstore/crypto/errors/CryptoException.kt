package app.passwordstore.crypto.errors

import app.passwordstore.crypto.KeyManager

public sealed class CryptoException(message: String? = null, cause: Throwable? = null) :
  Exception(message, cause)

/** Sealed exception types for [KeyManager]. */
public sealed class KeyManagerException(message: String? = null, cause: Throwable? = null) : CryptoException(message, cause)

/** Store contains no keys. */
public object NoKeysAvailableException : KeyManagerException("No keys were found")

/** Key directory does not exist or cannot be accessed. */
public object KeyDirectoryUnavailableException :
  KeyManagerException("Key directory does not exist")

/** Failed to delete given key. */
public object KeyDeletionFailedException : KeyManagerException("Couldn't delete the key file")

/** Failed to parse the key as a known type. */
public class InvalidKeyException(cause: Throwable? = null) :
  KeyManagerException("Given key cannot be parsed as a known key type", cause)

/** No key matching `keyId` could be found. */
public class KeyNotFoundException(keyId: String) :
  KeyManagerException("No key found with id: $keyId")

/** Attempting to add another key for `keyId` without requesting a replace. */
public class KeyAlreadyExistsException(keyId: String) :
  KeyManagerException("Pre-existing key was found for $keyId")

public class NoSecretKeyException(keyId: String) :
  KeyManagerException("No secret keys found for $keyId")

/** Sealed exception types for [app.passwordstore.crypto.CryptoHandler]. */
public sealed class CryptoHandlerException(message: String? = null, cause: Throwable? = null) :
  CryptoException(message, cause)

/** The passphrase provided for decryption was incorrect. */
public class IncorrectPassphraseException(cause: Throwable) : CryptoHandlerException(null, cause)

/** No keys were provided for encryption. */
public class NoKeysProvided(message: String?) : CryptoHandlerException(message, null)

/** An unexpected error that cannot be mapped to a known type. */
public class UnknownError(cause: Throwable) : CryptoHandlerException(null, cause)

public class KeySpecific(public val key: Any, cause: Throwable?) : CryptoHandlerException(key.toString(), cause)

/** Wrapper containing possibly multiple child exceptions via [suppressedExceptions]. */
public class MultipleKeySpecific(
  message: String?,
  public val errors: List<KeySpecific>
) : CryptoHandlerException(message) {
  init {
    for (error in errors) {
      addSuppressed(error)
    }
  }
}

/** Sealed exception types for [app.passwordstore.crypto.DeviceHandler]. */
public sealed class DeviceHandlerException(message: String? = null, cause: Throwable? = null) :
  CryptoHandlerException(message, cause)

/** The device crypto operation was canceled by the user. */
public class DeviceOperationCanceled(message: String) : DeviceHandlerException(message, null)

/** The device crypto operation failed. */
public class DeviceOperationFailed(message: String?, cause: Throwable? = null) : DeviceHandlerException(message, cause)

/** The device's key fingerprint doesn't match the fingerprint we are trying to pair it to. */
public class DeviceFingerprintMismatch(
  public val publicFingerprint: String,
  public val deviceFingerprint: String,
) : DeviceHandlerException()
