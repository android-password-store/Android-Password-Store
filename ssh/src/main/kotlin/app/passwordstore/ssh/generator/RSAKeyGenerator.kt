package app.passwordstore.ssh.generator

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.passwordstore.ssh.utils.Constants.KEYSTORE_ALIAS
import app.passwordstore.ssh.utils.Constants.PROVIDER_ANDROID_KEY_STORE
import java.security.KeyPair
import java.security.KeyPairGenerator

public class RSAKeyGenerator : SSHKeyGenerator {

  override suspend fun generateKey(requiresAuthentication: Boolean): KeyPair {
    val algorithm = KeyProperties.KEY_ALGORITHM_RSA
    // Generate Keystore-backed private key.
    val parameterSpec =
      KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN).run {
        setKeySize(RSA_KEY_SIZE)
        setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        setDigests(
          KeyProperties.DIGEST_SHA1,
          KeyProperties.DIGEST_SHA256,
          KeyProperties.DIGEST_SHA512,
        )
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
    private const val RSA_KEY_SIZE = 3072
  }
}
