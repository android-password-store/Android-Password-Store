package app.passwordstore.ssh.generator

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.passwordstore.ssh.utils.Constants.KEYSTORE_ALIAS
import app.passwordstore.ssh.utils.Constants.PROVIDER_ANDROID_KEY_STORE
import java.security.KeyPair
import java.security.KeyPairGenerator

public class ECDSAKeyGenerator(private val isStrongBoxSupported: Boolean) : SSHKeyGenerator {

  override suspend fun generateKey(requiresAuthentication: Boolean): KeyPair {
    val algorithm = KeyProperties.KEY_ALGORITHM_EC

    val parameterSpec =
      KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN).run {
        setKeySize(ECDSA_KEY_SIZE)
        setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
        setDigests(KeyProperties.DIGEST_SHA256)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          setIsStrongBoxBacked(isStrongBoxSupported)
        }
        if (requiresAuthentication) {
          setUserAuthenticationRequired(true)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setUserAuthenticationParameters(
              SSHKeyGenerator.USER_AUTHENTICATION_TIMEOUT,
              KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
          } else {
            @Suppress("DEPRECATION")
            setUserAuthenticationValidityDurationSeconds(
              SSHKeyGenerator.USER_AUTHENTICATION_TIMEOUT
            )
          }
        }
        build()
      }

    val keyPair =
      KeyPairGenerator.getInstance(algorithm, PROVIDER_ANDROID_KEY_STORE).run {
        initialize(parameterSpec)
        generateKeyPair()
      }

    return keyPair
  }

  private companion object {
    private const val ECDSA_KEY_SIZE = 256
  }
}
