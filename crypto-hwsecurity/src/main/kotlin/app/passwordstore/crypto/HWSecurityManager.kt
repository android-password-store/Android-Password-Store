package app.passwordstore.crypto

import android.app.Application
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.cotech.hw.SecurityKeyManager
import de.cotech.hw.SecurityKeyManagerConfig
import de.cotech.hw.openpgp.OpenPgpSecurityKey
import de.cotech.hw.openpgp.OpenPgpSecurityKeyDialogFragment
import de.cotech.hw.openpgp.internal.operations.PsoDecryptOp
import de.cotech.hw.secrets.ByteSecret
import de.cotech.hw.secrets.PinProvider
import de.cotech.hw.ui.SecurityKeyDialogInterface
import de.cotech.hw.ui.SecurityKeyDialogInterface.SecurityKeyDialogCallback
import de.cotech.hw.ui.SecurityKeyDialogOptions
import de.cotech.hw.ui.SecurityKeyDialogOptions.PinMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.bcpg.ECDHPublicBCPGKey
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.openpgp.PGPSessionKey
import org.pgpainless.algorithm.PublicKeyAlgorithm
import org.pgpainless.decryption_verification.HardwareSecurity.HardwareSecurityException

@Singleton
public class HWSecurityManager
@Inject
constructor(
  private val application: Application,
) {

  private val securityKeyManager: SecurityKeyManager by lazy { SecurityKeyManager.getInstance() }

  public fun init(enableLogging: Boolean = false) {
    securityKeyManager.init(
      application,
      SecurityKeyManagerConfig.Builder().setEnableDebugLogging(enableLogging).build()
    )
  }

  public fun isHardwareAvailable(): Boolean {
    return securityKeyManager.isNfcHardwareAvailable || securityKeyManager.isUsbHostModeAvailable
  }

  private suspend fun <T : Any> withOpenDevice(
    fragmentManager: FragmentManager,
    pinMode: PinMode,
    block: suspend (OpenPgpSecurityKey, PinProvider?) -> T
  ): T =
    withContext(Dispatchers.Main) {
      val fragment =
        OpenPgpSecurityKeyDialogFragment.newInstance(
          SecurityKeyDialogOptions.builder()
            .setPinMode(pinMode)
            .setFormFactor(SecurityKeyDialogOptions.FormFactor.SECURITY_KEY)
            .setPreventScreenshots(false) // TODO
            .build()
        )

      val deferred = CompletableDeferred<T>()

      fragment.setSecurityKeyDialogCallback(
        object : SecurityKeyDialogCallback<OpenPgpSecurityKey> {
          private var result: Result<T> = Result.failure(CancellationException())

          override fun onSecurityKeyDialogDiscovered(
            dialogInterface: SecurityKeyDialogInterface,
            securityKey: OpenPgpSecurityKey,
            pinProvider: PinProvider?
          ) {
            fragment.lifecycleScope.launch {
              fragment.repeatOnLifecycle(Lifecycle.State.CREATED) {
                runCatching {
                    fragment.postProgressMessage("Decrypting password entry")
                    result = Result.success(block(securityKey, pinProvider))
                    fragment.successAndDismiss()
                  }
                  .onFailure { e ->
                    when (e) {
                      is IOException -> fragment.postError(e)
                      else -> {
                        result = Result.failure(e)
                        fragment.dismiss()
                      }
                    }
                  }
              }
            }
          }

          override fun onSecurityKeyDialogCancel() {
            deferred.cancel()
          }

          override fun onSecurityKeyDialogDismiss() {
            deferred.completeWith(result)
          }
        }
      )

      fragment.show(fragmentManager)

      val value = deferred.await()
      // HWSecurity doesn't clean up fast enough for LeakCanary's liking.
      securityKeyManager.clearConnectedSecurityKeys()
      value
    }

  public suspend fun readDevice(fragmentManager: FragmentManager): HWSecurityDevice =
    withOpenDevice(fragmentManager, PinMode.NO_PIN_INPUT) { securityKey, _ ->
      securityKey.toDevice()
    }

  public suspend fun decryptSessionKey(
    fragmentManager: FragmentManager,
    encryptedSessionKey: PGPEncryptedSessionKey
  ): PGPSessionKey =
    withOpenDevice(fragmentManager, PinMode.PIN_INPUT) { securityKey, pinProvider ->
      val pin =
        pinProvider?.getPin(securityKey.openPgpInstanceAid)
          ?: throw HWSecurityException("PIN required for decryption")

      val contents =
        withContext(Dispatchers.IO) {
          when (val a = encryptedSessionKey.algorithm) {
            PublicKeyAlgorithm.RSA_GENERAL ->
              decryptSessionKeyRsa(encryptedSessionKey, securityKey, pin)
            PublicKeyAlgorithm.ECDH -> decryptSessionKeyEcdh(encryptedSessionKey, securityKey, pin)
            else -> throw HWSecurityException("Unsupported encryption algorithm: ${a.name}")
          }
        }

      PGPSessionKey(encryptedSessionKey.algorithm.algorithmId, contents)
    }
}

public class HWSecurityException(override val message: String) : HardwareSecurityException()

private fun decryptSessionKeyRsa(
  encryptedSessionKey: PGPEncryptedSessionKey,
  securityKey: OpenPgpSecurityKey,
  pin: ByteSecret,
): ByteArray {
  return PsoDecryptOp.create(securityKey.openPgpAppletConnection)
    .verifyAndDecryptSessionKey(pin, encryptedSessionKey.contents, 0, null)
}

@Suppress("MagicNumber")
private fun decryptSessionKeyEcdh(
  encryptedSessionKey: PGPEncryptedSessionKey,
  securityKey: OpenPgpSecurityKey,
  pin: ByteSecret,
): ByteArray {
  val key =
    encryptedSessionKey.publicKey.publicKeyPacket.key.run {
      this as? ECDHPublicBCPGKey
        ?: throw HWSecurityException("Expected ECDHPublicBCPGKey but got ${this::class.simpleName}")
    }
  val symmetricKeySize =
    when (val id = key.symmetricKeyAlgorithm.toInt()) {
      SymmetricKeyAlgorithmTags.AES_128 -> 128
      SymmetricKeyAlgorithmTags.AES_192 -> 192
      SymmetricKeyAlgorithmTags.AES_256 -> 256
      else -> throw HWSecurityException("Unexpected symmetric key algorithm: $id")
    }
  return PsoDecryptOp.create(securityKey.openPgpAppletConnection)
    .verifyAndDecryptSessionKey(pin, encryptedSessionKey.contents, symmetricKeySize, byteArrayOf())
}
