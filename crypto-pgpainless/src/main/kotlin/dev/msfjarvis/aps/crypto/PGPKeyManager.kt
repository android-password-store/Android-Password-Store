/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.crypto

import androidx.annotation.VisibleForTesting
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.pgpainless.PGPainless

public class PGPKeyManager(
  filesDir: String,
  private val dispatcher: CoroutineDispatcher,
) : KeyManager<PGPKeyPair> {

  private val keyDir = File(filesDir, KEY_DIR_NAME)

  override suspend fun addKey(key: PGPKeyPair, replace: Boolean): Result<PGPKeyPair, Throwable> =
    withContext(dispatcher) {
      runCatching {
        if (!keyDirExists()) throw KeyManagerException.KeyDirectoryUnavailableException
        val keyFile = File(keyDir, "${key.getKeyId()}.$KEY_EXTENSION")
        if (keyFile.exists()) {
          // Check for replace flag first and if it is false, throw an error
          if (!replace) throw KeyManagerException.KeyAlreadyExistsException(key.getKeyId())
          if (!keyFile.delete()) throw KeyManagerException.KeyDeletionFailedException
        }

        keyFile.writeBytes(key.getPrivateKey())

        key
      }
    }

  override suspend fun removeKey(key: PGPKeyPair): Result<PGPKeyPair, Throwable> =
    withContext(dispatcher) {
      runCatching {
        if (!keyDirExists()) throw KeyManagerException.KeyDirectoryUnavailableException
        val keyFile = File(keyDir, "${key.getKeyId()}.$KEY_EXTENSION")
        if (keyFile.exists()) {
          if (!keyFile.delete()) throw KeyManagerException.KeyDeletionFailedException
        }

        key
      }
    }

  override suspend fun getKeyById(id: String): Result<PGPKeyPair, Throwable> =
    withContext(dispatcher) {
      runCatching {
        if (!keyDirExists()) throw KeyManagerException.KeyDirectoryUnavailableException
        val keys = keyDir.listFiles()
        if (keys.isNullOrEmpty()) throw KeyManagerException.NoKeysAvailableException

        for (keyFile in keys) {
          val secretKeyRing = PGPainless.readKeyRing().secretKeyRing(keyFile.inputStream())
          val secretKey = secretKeyRing.secretKey
          val keyPair = PGPKeyPair(secretKey)
          if (keyPair.getKeyId() == id) return@runCatching keyPair
        }

        throw KeyManagerException.KeyNotFoundException(id)
      }
    }

  override suspend fun getAllKeys(): Result<List<PGPKeyPair>, Throwable> =
    withContext(dispatcher) {
      runCatching {
        if (!keyDirExists()) throw KeyManagerException.KeyDirectoryUnavailableException
        val keys = keyDir.listFiles()
        if (keys.isNullOrEmpty()) return@runCatching listOf()

        keys
          .map { keyFile ->
            val secretKeyRing = PGPainless.readKeyRing().secretKeyRing(keyFile.inputStream())
            val secretKey = secretKeyRing.secretKey

            PGPKeyPair(secretKey)
          }
          .toList()
      }
    }

  override fun canHandle(fileName: String): Boolean {
    // TODO: This is a temp hack for now and in future it should check that the GPGKeyManager can
    // decrypt the file
    return fileName.endsWith(KEY_EXTENSION)
  }

  private fun keyDirExists(): Boolean {
    return keyDir.exists() || keyDir.mkdirs()
  }

  internal companion object {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal const val KEY_DIR_NAME: String = "keys"
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal const val KEY_EXTENSION: String = "key"
  }
}
