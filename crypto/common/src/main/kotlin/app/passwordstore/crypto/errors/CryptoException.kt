/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto.errors

import app.passwordstore.crypto.KeyManager

public sealed class CryptoException(message: String? = null, cause: Throwable? = null) :
  Exception(message, cause)

/** Sealed exception types for [KeyManager]. */
public sealed class KeyManagerException(message: String? = null) : CryptoException(message)

/** Store contains no keys. */
public data object NoKeysAvailableException : KeyManagerException("No keys were found")

/** Key directory does not exist or cannot be accessed. */
public data object KeyDirectoryUnavailableException :
  KeyManagerException("Key directory does not exist")

/** Failed to delete given key. */
public data object KeyDeletionFailedException : KeyManagerException("Couldn't delete the key file")

/** Failed to parse the key as a known type. */
public data object InvalidKeyException :
  KeyManagerException("Given key cannot be parsed as a known key type")

/** Key failed the [app.passwordstore.crypto.KeyUtils.isKeyUsable] test. */
public data object UnusableKeyException :
  KeyManagerException("Given key is not usable for encryption - is it using AEAD?")

/** No key matching `keyId` could be found. */
public class KeyNotFoundException(keyId: String) :
  KeyManagerException("No key found with id: $keyId")

/** Attempting to add another key for `keyId` without requesting a replace. */
public class KeyAlreadyExistsException(keyId: String) :
  KeyManagerException("Pre-existing key was found for $keyId")

/** Sealed exception types for [app.passwordstore.crypto.CryptoHandler]. */
public sealed class CryptoHandlerException(message: String? = null, cause: Throwable? = null) :
  CryptoException(message, cause)

/** The passphrase provided for decryption was incorrect. */
public class IncorrectPassphraseException(cause: Throwable) : CryptoHandlerException(null, cause)

/** The encrypted material is using an incompatible variant of PGP's AEAD standard. */
public class NonStandardAEAD(cause: Throwable) :
  CryptoHandlerException("GnuPG's AEAD implementation is non-standard and unsupported", cause)

/** No keys were passed to the encrypt/decrypt operation. */
public data object NoKeysProvidedException : CryptoHandlerException(null, null)

/** An unexpected error that cannot be mapped to a known type. */
public class UnknownError(cause: Throwable, message: String? = null) :
  CryptoHandlerException(message, cause)
