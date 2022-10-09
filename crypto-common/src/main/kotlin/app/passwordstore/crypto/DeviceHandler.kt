package app.passwordstore.crypto

import app.passwordstore.crypto.errors.DeviceHandlerException
import com.github.michaelbull.result.Result

public interface DeviceHandler<Key, EncryptedSessionKey, DecryptedSessionKey> {
  public suspend fun pairWithPublicKey(publicKey: Key): Result<Key, DeviceHandlerException>

  public suspend fun decryptSessionKey(
    encryptedSessionKey: EncryptedSessionKey
  ): Result<DecryptedSessionKey, DeviceHandlerException>
}
