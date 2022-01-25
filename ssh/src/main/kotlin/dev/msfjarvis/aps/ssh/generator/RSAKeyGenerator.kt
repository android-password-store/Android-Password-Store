package dev.msfjarvis.aps.ssh.generator

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.msfjarvis.aps.ssh.utils.Constants.KEYSTORE_ALIAS
import dev.msfjarvis.aps.ssh.utils.Constants.PROVIDER_ANDROID_KEY_STORE
import java.security.KeyPair
import java.security.KeyPairGenerator

class RSAKeyGenerator : SSHKeyGenerator {

  override suspend fun generateKey(requiresAuthentication: Boolean): KeyPair {
    val algorithm = KeyProperties.KEY_ALGORITHM_RSA
    // Generate Keystore-backed private key.
    val parameterSpec =
      KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN).run {
        setKeySize(3072)
        setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        setDigests(
          KeyProperties.DIGEST_SHA1,
          KeyProperties.DIGEST_SHA256,
          KeyProperties.DIGEST_SHA512,
        )
        if (requiresAuthentication) {
          setUserAuthenticationRequired(true)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setUserAuthenticationParameters(30, KeyProperties.AUTH_DEVICE_CREDENTIAL)
          } else {
            @Suppress("DEPRECATION") setUserAuthenticationValidityDurationSeconds(30)
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
}
