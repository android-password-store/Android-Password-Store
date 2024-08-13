/*
 * Copyright © 2014-2024 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package app.passwordstore.crypto

import com.github.michaelbull.result.Result

/**
 * [KeyManager] defines a contract for implementing a management system for [Key]s as they would be
 * used by an implementation of [CryptoHandler] to obtain eligible public or private keys as
 * required.
 */
public interface KeyManager<Key, KeyIdentifier> {

  /**
   * Inserts a [key] into the store. If the key already exists, this method will return
   * [app.passwordstore.crypto.errors.KeyAlreadyExistsException] unless [replace] is `true`.
   */
  public suspend fun addKey(key: Key, replace: Boolean = false): Result<Key, Throwable>

  /** Finds a key for [identifier] in the store and deletes it. */
  public suspend fun removeKey(identifier: KeyIdentifier): Result<Unit, Throwable>

  /**
   * Get a [Key] for the given [id]. The actual semantics of what [id] is are left to individual
   * implementations to figure out for themselves. For example, in GPG this can be a full
   * hexadecimal key ID, an email, a short hex key ID, and probably a few more things.
   */
  public suspend fun getKeyById(id: KeyIdentifier): Result<Key, Throwable>

  /** Returns all keys currently in the store as a [List]. */
  public suspend fun getAllKeys(): Result<List<Key>, Throwable>

  /**
   * Get a stable identifier for the given [key]. The returned key ID should be suitable to be used
   * as an identifier for the cryptographic identity tied to this key.
   */
  public suspend fun getKeyId(key: Key): KeyIdentifier?
}
