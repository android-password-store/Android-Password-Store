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
import android.security.keystore.StrongBoxUnavailableException
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.i
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.common.SSHRuntimeException
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.signature.Signature
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1OutputStream
import org.bouncycastle.asn1.DERSequence
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SignatureException
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

/**
 * Based on SSHJ's AbstractSignature.java, which licensed under the Apache License, Version 2.0,
 * with the following copyright notice:
 * Copyright (C)2009 - SSHJ Contributors
 */
private abstract class AndroidKeystoreCompatAbstractSignature : Signature {

    lateinit var signature: java.security.Signature

    abstract fun getAlgorithm(key: Key): String

    fun initBasedOnKeyType(key: Key) {
        signature = try {
            if (key.javaClass.simpleName.startsWith("AndroidKeyStore")) {
                java.security.Signature.getInstance(getAlgorithm(key))
            } else {
                SecurityUtils.getSignature(getAlgorithm(key))
            }
        } catch (e: GeneralSecurityException) {
            throw SSHRuntimeException(e)
        }
    }

    override fun update(H: ByteArray) {
        update(H, 0, H.size)
    }

    override fun update(H: ByteArray, off: Int, len: Int) {
        try {
            signature.update(H, off, len)
        } catch (e: SignatureException) {
            throw SSHRuntimeException(e)
        }
    }

    override fun initSign(privateKey: PrivateKey) {
        initBasedOnKeyType(privateKey)
        try {
            signature.initSign(privateKey)
        } catch (e: InvalidKeyException) {
            throw SSHRuntimeException(e)
        }
    }

    override fun sign(): ByteArray {
        return try {
            signature.sign()
        } catch (e: SignatureException) {
            throw SSHRuntimeException(e)
        }
    }

    protected fun extractSig(sig: ByteArray, expectedKeyAlgorithm: String): ByteArray {
        val buffer = Buffer.PlainBuffer(sig)
        return try {
            val algo = buffer.readString()
            if (expectedKeyAlgorithm != algo) {
                throw SSHRuntimeException("Expected '$expectedKeyAlgorithm' key algorithm, but got: $algo")
            }
            buffer.readBytes()
        } catch (e: Buffer.BufferException) {
            throw SSHRuntimeException(e)
        }
    }

    override fun initVerify(publicKey: PublicKey) {
        initBasedOnKeyType(publicKey)
        try {
            signature.initVerify(publicKey)
        } catch (e: InvalidKeyException) {
            throw SSHRuntimeException(e)
        }
    }
}

/**
 * Based on SSHJ's SignatureRSA.java, which licensed under the Apache License, Version 2.0, with
 * the following copyright notice:
 * Copyright (C)2009 - SSHJ Contributors
 */
object AndroidKeystoreCompatRsaSignatureFactory : net.schmizz.sshj.common.Factory.Named<Signature> {

    override fun getName() = KeyType.RSA.toString()

    override fun create(): Signature {
        return object : AndroidKeystoreCompatAbstractSignature() {

            override fun getAlgorithm(key: Key) = "SHA1withRSA"

            override fun verify(sig: ByteArray): Boolean {
                val extractedSig = extractSig(sig, name)
                return try {
                    signature.verify(extractedSig)
                } catch (e: SignatureException) {
                    throw SSHRuntimeException(e)
                }
            }

            override fun encode(signature: ByteArray) = signature
        }
    }
}

/**
 * Based on SSHJ's SignatureECDSA.java, which licensed under the Apache License, Version 2.0, with
 * the following copyright notice:
 * Copyright (C)2009 - SSHJ Contributors
 */
class AndroidKeystoreCompatEcdsaSignatureFactory(val keyType: KeyType) : net.schmizz.sshj.common.Factory.Named<Signature> {

    init {
        require(keyType == KeyType.ECDSA256 || keyType == KeyType.ECDSA384 || keyType == KeyType.ECDSA521)
    }

    override fun getName() = keyType.toString()

    override fun create(): Signature {
        return object : AndroidKeystoreCompatAbstractSignature() {

            override fun getAlgorithm(key: Key) = when (keyType) {
                KeyType.ECDSA256 -> "SHA256withECDSA"
                KeyType.ECDSA384 -> "SHA384withECDSA"
                KeyType.ECDSA521 -> "SHA512withECDSA"
                else -> throw IllegalStateException()
            }

            override fun encode(sig: ByteArray): ByteArray {
                var rIndex = 3
                val rLen: Int = sig[rIndex++].toInt() and 0xff
                val r = ByteArray(rLen)
                System.arraycopy(sig, rIndex, r, 0, r.size)
                var sIndex = rIndex + rLen + 1
                val sLen: Int = sig[sIndex++].toInt() and 0xff
                val s = ByteArray(sLen)
                System.arraycopy(sig, sIndex, s, 0, s.size)
                System.arraycopy(sig, 4, r, 0, rLen)
                System.arraycopy(sig, 6 + rLen, s, 0, sLen)
                val buf = Buffer.PlainBuffer()
                buf.putMPInt(BigInteger(r))
                buf.putMPInt(BigInteger(s))
                return buf.compactData
            }

            override fun verify(sig: ByteArray?): Boolean {
                return try {
                    val sigBlob = extractSig(sig!!, name)
                    signature.verify(asnEncode(sigBlob))
                } catch (e: SignatureException) {
                    throw SSHRuntimeException(e)
                } catch (e: IOException) {
                    throw SSHRuntimeException(e)
                }
            }

            private fun asnEncode(sigBlob: ByteArray): ByteArray? {
                val sigbuf = Buffer.PlainBuffer(sigBlob)
                val r = sigbuf.readBytes()
                val s = sigbuf.readBytes()
                val vector = ASN1EncodableVector()
                vector.add(ASN1Integer(r))
                vector.add(ASN1Integer(s))
                val baos = ByteArrayOutputStream()
                val asnOS = ASN1OutputStream(baos)
                asnOS.writeObject(DERSequence(vector))
                asnOS.flush()
                return baos.toByteArray()
            }
        }
    }
}
