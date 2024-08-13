package app.passwordstore.crypto

import com.github.michaelbull.result.Result

/** [KeyManager] implementation for [kage.Age]-based keys. */
public class AgeKeyManager : KeyManager<AgeKey, String> {

  override suspend fun addKey(key: AgeKey, replace: Boolean): Result<AgeKey, Throwable> {
    TODO("Not yet implemented")
  }

  override suspend fun removeKey(identifier: String): Result<Unit, Throwable> {
    TODO("Not yet implemented")
  }

  override suspend fun getKeyById(id: String): Result<AgeKey, Throwable> {
    TODO("Not yet implemented")
  }

  override suspend fun getAllKeys(): Result<List<AgeKey>, Throwable> {
    TODO("Not yet implemented")
  }

  override suspend fun getKeyId(key: AgeKey): String? {
    TODO("Not yet implemented")
  }
}
