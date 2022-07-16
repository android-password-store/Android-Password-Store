/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("BlockingMethodInNonBlockingContext")

package app.passwordstore.crypto

import androidx.annotation.VisibleForTesting
import app.passwordstore.crypto.KeyUtils.tryGetId
import app.passwordstore.crypto.KeyUtils.tryParseKeyring
import app.passwordstore.crypto.errors.InvalidKeyException
import app.passwordstore.crypto.errors.KeyAlreadyExistsException
import app.passwordstore.crypto.errors.KeyDeletionFailedException
import app.passwordstore.crypto.errors.KeyDirectoryUnavailableException
import app.passwordstore.crypto.errors.KeyNotFoundException
import app.passwordstore.crypto.errors.NoKeysAvailableException
import app.passwordstore.util.coroutines.runSuspendCatching
import com.github.michaelbull.result.Result
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.pgpainless.util.selection.userid.SelectUserId

public class PGPKeyManager
@Inject
constructor(
  filesDir: String,
  private val dispatcher: CoroutineDispatcher,
) : KeyManager<PGPKey, GpgIdentifier> {

  private val keyDir = File(filesDir, KEY_DIR_NAME)

  override suspend fun addKey(key: PGPKey, replace: Boolean): Result<PGPKey, Throwable> =
    withContext(dispatcher) {
      runSuspendCatching {
        if (!keyDirExists()) throw KeyDirectoryUnavailableException
        if (tryParseKeyring(key) == null) throw InvalidKeyException
        val keyFile = File(keyDir, "${tryGetId(key)}.$KEY_EXTENSION")
        if (keyFile.exists()) {
          // Check for replace flag first and if it is false, throw an error
          if (!replace)
            throw KeyAlreadyExistsException(
              tryGetId(key)?.toString() ?: "Failed to retrieve key ID"
            )
          if (!keyFile.delete()) throw KeyDeletionFailedException
        }

        keyFile.writeBytes(key.contents)

        key
      }
    }

  override suspend fun removeKey(key: PGPKey): Result<PGPKey, Throwable> =
    withContext(dispatcher) {
      runSuspendCatching {
        if (!keyDirExists()) throw KeyDirectoryUnavailableException
        if (tryParseKeyring(key) == null) throw InvalidKeyException
        val keyFile = File(keyDir, "${tryGetId(key)}.$KEY_EXTENSION")
        if (keyFile.exists()) {
          if (!keyFile.delete()) throw KeyDeletionFailedException
        }

        key
      }
    }

  override suspend fun getKeyById(id: GpgIdentifier): Result<PGPKey, Throwable> =
    withContext(dispatcher) {
      runSuspendCatching {
        if (!keyDirExists()) throw KeyDirectoryUnavailableException
        val keyFiles = keyDir.listFiles()
        if (keyFiles.isNullOrEmpty()) throw NoKeysAvailableException
        val keys = keyFiles.map { file -> PGPKey(file.readBytes()) }

        val matchResult =
          when (id) {
            is GpgIdentifier.KeyId -> {
              val keyIdMatch =
                keys
                  .map { key -> key to tryGetId(key) }
                  .firstOrNull { (_, keyId) -> keyId?.id == id.id }
              keyIdMatch?.first
            }
            is GpgIdentifier.UserId -> {
              val selector = SelectUserId.byEmail(id.email)
              val userIdMatch =
                keys
                  .map { key -> key to tryParseKeyring(key) }
                  .firstOrNull { (_, keyRing) -> selector.firstMatch(keyRing) != null }
              userIdMatch?.first
            }
          }

        if (matchResult != null) {
          return@runSuspendCatching matchResult
        }

        throw KeyNotFoundException("$id")
      }
    }

  override suspend fun getAllKeys(): Result<List<PGPKey>, Throwable> =
    withContext(dispatcher) {
      runSuspendCatching {
        if (!keyDirExists()) throw KeyDirectoryUnavailableException
        val keyFiles = keyDir.listFiles()
        if (keyFiles.isNullOrEmpty()) return@runSuspendCatching emptyList()
        keyFiles.map { keyFile -> PGPKey(keyFile.readBytes()) }.toList()
      }
    }

  override suspend fun getKeyId(key: PGPKey): GpgIdentifier? = tryGetId(key)

  // TODO: This is a temp hack for now and in future it should check that the GPGKeyManager can
  // decrypt the file.
  override fun canHandle(fileName: String): Boolean {
    return fileName.endsWith(KEY_EXTENSION)
  }

  /** Checks if [keyDir] exists and attempts to create it if not. */
  private fun keyDirExists(): Boolean {
    return keyDir.exists() || keyDir.mkdirs()
  }

  public companion object {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal const val KEY_DIR_NAME: String = "keys"
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal const val KEY_EXTENSION: String = "key"
  }
}