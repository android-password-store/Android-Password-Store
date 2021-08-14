/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.data.crypto

import androidx.annotation.VisibleForTesting
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.runCatching
import com.proton.Gopenpgp.crypto.Crypto
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

public class GPGKeyManager(filesDir: String, private val dispatcher: CoroutineDispatcher) :
  KeyManager<GPGKeyPair> {

  private val keyDir = File(filesDir, KEY_DIR_NAME)
  override suspend fun addKey(key: GPGKeyPair): Result<String, Throwable> =
    withContext(dispatcher) {
      runCatching {
        if (!keyDirExists()) error("Key directory does not exist")
        val keyFile = File(keyDir, "${key.getKeyId()}.$KEY_EXTENSION")
        if (keyFile.exists()) {
          if (!keyFile.delete()) error("Couldn't delete existing key file with the same name")
        }

        keyFile.writeBytes(key.getPrivateKey())

        key.getKeyId()
      }
    }

  override suspend fun removeKey(key: GPGKeyPair): Result<String, Throwable> =
    withContext(dispatcher) {
      runCatching {
        if (!keyDirExists()) error("Key directory does not exist")
        val keyFile = File(keyDir, "${key.getKeyId()}.$KEY_EXTENSION")
        if (keyFile.exists()) {
          if (!keyFile.delete()) error("Couldn't delete key file")
        }

        key.getKeyId()
      }
    }

  override suspend fun getKeyById(id: String): Result<GPGKeyPair, Throwable> =
    withContext(dispatcher) {
      runCatching {
        if (!keyDirExists()) error("Key directory does not exist")
        val keys = keyDir.listFiles()
        if (keys.isNullOrEmpty()) error("No keys were found")

        for (keyFile in keys) {
          val keyPair = GPGKeyPair(Crypto.newKeyFromArmored(keyFile.readText()))
          if (keyPair.getKeyId() == id) return@runCatching keyPair
        }

        error("No key found with id: $id")
      }
    }

  override suspend fun getAllKeys(): Result<List<GPGKeyPair>, Throwable> =
    withContext(dispatcher) {
      runCatching {
        if (!keyDirExists()) error("Key directory does not exist")
        val keys = keyDir.listFiles()
        if (keys.isNullOrEmpty()) return@runCatching listOf()

        keys.map { GPGKeyPair(Crypto.newKeyFromArmored(it.readText())) }.toList()
      }
    }

  override fun canHandle(fileName: String): Boolean {
    return fileName.endsWith(KEY_EXTENSION)
  }

  private fun keyDirExists(): Boolean {
    return keyDir.exists() || keyDir.mkdirs()
  }

  internal companion object {
    @VisibleForTesting internal const val KEY_DIR_NAME: String = "keys"
    @VisibleForTesting internal const val KEY_EXTENSION: String = "key"
  }
}
