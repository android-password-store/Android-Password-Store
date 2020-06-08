/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.config

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.i
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.UnrecoverableKeyException


const val PROVIDER_ANDROID_KEY_STORE = "AndroidKeyStore"

private val androidKeystore: KeyStore by lazy {
    KeyStore.getInstance(PROVIDER_ANDROID_KEY_STORE).apply { load(null) }
}

private fun KeyStore.getPrivateKey(keyAlias: String) = getKey(keyAlias, null) as? PrivateKey

private fun KeyStore.getPublicKey(keyAlias: String) = getCertificate(keyAlias)?.publicKey

enum class SshKeyGenType(private val algorithm: String, private val keyLength: Int,
                         private val applyToSpec: KeyGenParameterSpec.Builder.() -> Unit) {

    Rsa2048(KeyProperties.KEY_ALGORITHM_RSA, 2048, {
        setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
    }),
    Rsa3072(KeyProperties.KEY_ALGORITHM_RSA, 3072, {
        setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
    }),
    Rsa4096(KeyProperties.KEY_ALGORITHM_RSA, 4096, {
        setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
    }),
    Ecdsa256(KeyProperties.KEY_ALGORITHM_EC, 256, {
        setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
        setDigests(KeyProperties.DIGEST_SHA256)
    }),
    Ecdsa384(KeyProperties.KEY_ALGORITHM_EC, 384, {
        setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp384r1"))
        setDigests(KeyProperties.DIGEST_SHA384)
    }),
    Ecdsa521(KeyProperties.KEY_ALGORITHM_EC, 521, {
        setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp521r1"))
        setDigests(KeyProperties.DIGEST_SHA512)
    });

    private fun generateKeyPair(keyAlias: String, requireAuthentication: Boolean, useStrongBox: Boolean): KeyPair {
        val parameterSpec = KeyGenParameterSpec.Builder(
            keyAlias, KeyProperties.PURPOSE_SIGN
        ).run {
            setKeySize(keyLength)
            apply(applyToSpec)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setIsStrongBoxBacked(useStrongBox)
            }
            if (requireAuthentication) {
                setUserAuthenticationRequired(true)
                // 60 seconds should provide ample time to connect to the SSH server and
                // perform authentication (and possibly a Git operation and another connect,
                // in case of the clone operation).
                setUserAuthenticationValidityDurationSeconds(60)
            }
            build()
        }
        return KeyPairGenerator.getInstance(algorithm, PROVIDER_ANDROID_KEY_STORE).run {
            initialize(parameterSpec)
            generateKeyPair()
        }
    }

    fun generateKeyPair(keyAlias: String, requireAuthentication: Boolean): KeyPair {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                return generateKeyPair(
                    keyAlias, requireAuthentication = requireAuthentication, useStrongBox = true)
            } catch (error: Exception) {
                i { "Falling back to non-StrongBox Keystore key" }
            }
        }
        return generateKeyPair(
            keyAlias, requireAuthentication = requireAuthentication, useStrongBox = false)
    }
}


class AndroidKeystoreKeyProvider(private val keyAlias: String) : KeyProvider {

    override fun getPublic(): PublicKey = try {
        androidKeystore.getPublicKey(keyAlias)!!
    } catch (error: Exception) {
        e(error)
        throw IOException("Failed to get public key '$keyAlias' from Android Keystore")
    }

    override fun getType(): KeyType = KeyType.fromKey(public)

    override fun getPrivate(): PrivateKey = try {
        androidKeystore.getPrivateKey(keyAlias)!!
    } catch (error: Exception) {
        e(error)
        throw IOException("Failed to access private key '$keyAlias' from Android Keystore")
    }

    companion object {
        fun isUserAuthenticationRequired(keyAlias: String): Boolean? {
            return try {
                val key = androidKeystore.getPrivateKey(keyAlias) ?: return null
                val factory = KeyFactory.getInstance(key.algorithm, PROVIDER_ANDROID_KEY_STORE)
                factory.getKeySpec(key, KeyInfo::class.java).isUserAuthenticationRequired
            } catch (error: Exception) {
                if (error is KeyPermanentlyInvalidatedException || error is UnrecoverableKeyException) {
                    // The user deactivated their screen lock, which invalidates the key. We delete
                    // it and pretend we didn't find it.
                    androidKeystore.deleteEntry(keyAlias)
                    return null
                }
                // It is fine to swallow the exception here since it will reappear when the key is
                // used for authentication and can then be shown in the UI.
                true
            }
        }
    }
}
