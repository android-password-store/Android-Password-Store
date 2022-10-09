package app.passwordstore.crypto

import androidx.fragment.app.FragmentManager
import app.passwordstore.crypto.errors.DeviceFingerprintMismatch
import app.passwordstore.crypto.errors.DeviceHandlerException
import app.passwordstore.crypto.errors.DeviceOperationFailed
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import org.bouncycastle.openpgp.PGPSessionKey

public class HWSecurityDeviceHandler(
  private val deviceManager: HWSecurityManager,
  private val fragmentManager: FragmentManager,
) : DeviceHandler<PGPKey, PGPEncryptedSessionKey, PGPSessionKey> {

  override suspend fun pairWithPublicKey(
    publicKey: PGPKey
  ): Result<PGPKey, DeviceHandlerException> = runCatching {
    val publicFingerprint = KeyUtils.tryGetEncryptionKeyFingerprint(publicKey)
      ?: throw DeviceOperationFailed("Failed to get encryption key fingerprint")
    val device = deviceManager.readDevice(fragmentManager)
    if (publicFingerprint != device.encryptKeyInfo?.fingerprint) {
      throw DeviceFingerprintMismatch(
        publicFingerprint.toString(),
        device.encryptKeyInfo?.fingerprint?.toString() ?: "Missing encryption key"
      )
    }
    KeyUtils.tryCreateStubKey(
      publicKey,
      device.id.serialNumber,
      listOfNotNull(
        device.encryptKeyInfo.fingerprint,
        device.signKeyInfo?.fingerprint,
        device.authKeyInfo?.fingerprint
      )
    ) ?: throw DeviceOperationFailed("Failed to create stub secret key")
  }.mapError { error ->
    when (error) {
      is DeviceHandlerException -> error
      else -> DeviceOperationFailed("Failed to pair device", error)
    }
  }

  override suspend fun decryptSessionKey(
    encryptedSessionKey: PGPEncryptedSessionKey
  ): Result<PGPSessionKey, DeviceHandlerException> = runCatching {
    deviceManager.decryptSessionKey(fragmentManager, encryptedSessionKey)
  }.mapError { error ->
    DeviceOperationFailed("Failed to decrypt session key", error)
  }
}
