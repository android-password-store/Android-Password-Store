// SPDX-FileCopyrightText: 2022 Paul Schaub <vanitasvitae@fsfe.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.bouncycastle

import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.operator.PublicKeyDataDecryptorFactory
import org.bouncycastle.util.encoders.Base64

/**
 * Implementation of the [PublicKeyDataDecryptorFactory] which caches decrypted session keys.
 * That way, if a message needs to be decrypted multiple times, expensive private key operations can be omitted.
 *
 * This implementation changes the behavior or [.recoverSessionData] to first return any
 * cache hits.
 * If no hit is found, the method call is delegated to the underlying [PublicKeyDataDecryptorFactory].
 * The result of that is then placed in the cache and returned.
 *
 * TODO: Do we also cache invalid session keys?
 */
public class CachingPublicKeyDataDecryptorFactory(
  private val factory: PublicKeyDataDecryptorFactory
) : PublicKeyDataDecryptorFactory by factory {

  private val cachedSessionKeys: MutableMap<String, ByteArray> = mutableMapOf()

  @Throws(PGPException::class)
  override fun recoverSessionData(keyAlgorithm: Int, secKeyData: Array<ByteArray>): ByteArray {
    return cachedSessionKeys.getOrPut(cacheKey(secKeyData)) {
      factory.recoverSessionData(keyAlgorithm, secKeyData)
    }.copy()
  }

  public fun clear() {
    cachedSessionKeys.clear()
  }

  private companion object {
    fun cacheKey(secKeyData: Array<ByteArray>): String {
      return Base64.toBase64String(secKeyData[0])
    }

    private fun ByteArray.copy(): ByteArray {
      val copy = ByteArray(size)
      System.arraycopy(this, 0, copy, 0, copy.size)
      return copy
    }
  }
}
