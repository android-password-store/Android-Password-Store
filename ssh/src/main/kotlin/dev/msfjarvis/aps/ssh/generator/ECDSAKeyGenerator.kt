package dev.msfjarvis.aps.ssh.generator

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.msfjarvis.aps.ssh.utils.Constants.KEYSTORE_ALIAS
import dev.msfjarvis.aps.ssh.utils.Constants.PROVIDER_ANDROID_KEY_STORE
import java.security.KeyPair
import java.security.KeyPairGenerator

public class ECDSAKeyGenerator(private val isStrongBoxSupported: Boolean) : SSHKeyGenerator {

  override suspend fun generateKey(requiresAuthentication: Boolean): KeyPair {
    val algorithm = KeyProperties.KEY_ALGORITHM_EC

    val parameterSpec =
      KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN).run {
        setKeySize(256)
        setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
        setDigests(KeyProperties.DIGEST_SHA256)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          setIsStrongBoxBacked(isStrongBoxSupported)
        }
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
