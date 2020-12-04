/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.git.sshj

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.runCatching
import dev.msfjarvis.aps.Application
import dev.msfjarvis.aps.R
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import dev.msfjarvis.aps.util.extensions.getEncryptedGitPrefs
import dev.msfjarvis.aps.util.extensions.getString
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import java.io.File
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.KeyProvider

private const val PROVIDER_ANDROID_KEY_STORE = "AndroidKeyStore"
private const val KEYSTORE_ALIAS = "sshkey"
private const val ANDROIDX_SECURITY_KEYSET_PREF_NAME = "androidx_sshkey_keyset_prefs"

private val androidKeystore: KeyStore by lazy(LazyThreadSafetyMode.NONE) {
    KeyStore.getInstance(PROVIDER_ANDROID_KEY_STORE).apply { load(null) }
}

private val KeyStore.sshPrivateKey
    get() = getKey(KEYSTORE_ALIAS, null) as? PrivateKey

private val KeyStore.sshPublicKey
    get() = getCertificate(KEYSTORE_ALIAS)?.publicKey

fun parseSshPublicKey(sshPublicKey: String): PublicKey? {
    val sshKeyParts = sshPublicKey.split("""\s+""".toRegex())
    if (sshKeyParts.size < 2)
        return null
    return Buffer.PlainBuffer(Base64.decode(sshKeyParts[1], Base64.NO_WRAP)).readPublicKey()
}

fun toSshPublicKey(publicKey: PublicKey): String {
    val rawPublicKey = Buffer.PlainBuffer().putPublicKey(publicKey).compactData
    val keyType = KeyType.fromKey(publicKey)
    return "$keyType ${Base64.encodeToString(rawPublicKey, Base64.NO_WRAP)}"
}

object SshKey {

    val sshPublicKey
        get() = if (publicKeyFile.exists()) publicKeyFile.readText() else null
    val canShowSshPublicKey
        get() = type in listOf(Type.LegacyGenerated, Type.KeystoreNative, Type.KeystoreWrappedEd25519)
    val exists
        get() = type != null
    val mustAuthenticate: Boolean
        get() {
            return runCatching {
                if (type !in listOf(Type.KeystoreNative, Type.KeystoreWrappedEd25519))
                    return false
                when (val key = androidKeystore.getKey(KEYSTORE_ALIAS, null)) {
                    is PrivateKey -> {
                        val factory = KeyFactory.getInstance(key.algorithm, PROVIDER_ANDROID_KEY_STORE)
                        return factory.getKeySpec(key, KeyInfo::class.java).isUserAuthenticationRequired
                    }
                    is SecretKey -> {
                        val factory = SecretKeyFactory.getInstance(key.algorithm, PROVIDER_ANDROID_KEY_STORE)
                        (factory.getKeySpec(key, KeyInfo::class.java) as KeyInfo).isUserAuthenticationRequired
                    }
                    else -> throw IllegalStateException("SSH key does not exist in Keystore")
                }
            }.getOrElse { error ->
                // It is fine to swallow the exception here since it will reappear when the key is
                // used for SSH authentication and can then be shown in the UI.
                d(error)
                false
            }
        }

    private val context: Context
        get() = Application.instance.applicationContext

    private val privateKeyFile
        get() = File(context.filesDir, ".ssh_key")
    private val publicKeyFile
        get() = File(context.filesDir, ".ssh_key.pub")

    private var type: Type?
        get() = Type.fromValue(context.sharedPrefs.getString(PreferenceKeys.GIT_REMOTE_KEY_TYPE))
        set(value) = context.sharedPrefs.edit {
            putString(PreferenceKeys.GIT_REMOTE_KEY_TYPE, value?.value)
        }

    private val isStrongBoxSupported by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        else
            false
    }

    private enum class Type(val value: String) {
        Imported("imported"),
        KeystoreNative("keystore_native"),
        KeystoreWrappedEd25519("keystore_wrapped_ed25519"),

        // Behaves like `Imported`, but allows to view the public key.
        LegacyGenerated("legacy_generated"),
        ;

        companion object {

            fun fromValue(value: String?): Type? = values().associateBy { it.value }[value]
        }
    }

    enum class Algorithm(val algorithm: String, val applyToSpec: KeyGenParameterSpec.Builder.() -> Unit) {
        Rsa(KeyProperties.KEY_ALGORITHM_RSA, {
            setKeySize(3072)
            setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
        }),
        Ecdsa(KeyProperties.KEY_ALGORITHM_EC, {
            setKeySize(256)
            setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            setDigests(KeyProperties.DIGEST_SHA256)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setIsStrongBoxBacked(isStrongBoxSupported)
            }
        }),
    }

    private fun delete() {
        androidKeystore.deleteEntry(KEYSTORE_ALIAS)
        // Remove Tink key set used by AndroidX's EncryptedFile.
        context.getSharedPreferences(ANDROIDX_SECURITY_KEYSET_PREF_NAME, Context.MODE_PRIVATE).edit {
            clear()
        }
        if (privateKeyFile.isFile) {
            privateKeyFile.delete()
        }
        if (publicKeyFile.isFile) {
            publicKeyFile.delete()
        }
        context.getEncryptedGitPrefs().edit {
            remove(PreferenceKeys.SSH_KEY_LOCAL_PASSPHRASE)
        }
        type = null
    }

    fun import(uri: Uri) {
        // First check whether the content at uri is likely an SSH private key.
        val fileSize = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                // Cursor returns only a single row.
                cursor.moveToFirst()
                cursor.getInt(0)
            } ?: throw IOException(context.getString(R.string.ssh_key_does_not_exist))

        // We assume that an SSH key's ideal size is > 0 bytes && < 100 kilobytes.
        if (fileSize > 100_000 || fileSize == 0)
            throw IllegalArgumentException(context.getString(R.string.ssh_key_import_error_not_an_ssh_key_message))

        val sshKeyInputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException(context.getString(R.string.ssh_key_does_not_exist))
        val lines = sshKeyInputStream.bufferedReader().readLines()

        // The file must have more than 2 lines, and the first and last line must have private key
        // markers.
        if (lines.size < 2 ||
            !Regex("BEGIN .* PRIVATE KEY").containsMatchIn(lines.first()) ||
            !Regex("END .* PRIVATE KEY").containsMatchIn(lines.last())
        )
            throw IllegalArgumentException(context.getString(R.string.ssh_key_import_error_not_an_ssh_key_message))

        // At this point, we are reasonably confident that we have actually been provided a private
        // key and delete the old key.
        delete()
        // Canonicalize line endings to '\n'.
        privateKeyFile.writeText(lines.joinToString("\n"))

        type = Type.Imported
    }

    @Deprecated("To be used only in Migrations.kt")
    fun useLegacyKey(isGenerated: Boolean) {
        type = if (isGenerated) Type.LegacyGenerated else Type.Imported
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun getOrCreateWrappingMasterKey(requireAuthentication: Boolean) = withContext(Dispatchers.IO) {
        MasterKey.Builder(context, KEYSTORE_ALIAS).run {
            setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            setRequestStrongBoxBacked(true)
            setUserAuthenticationRequired(requireAuthentication, 15)
            build()
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun getOrCreateWrappedPrivateKeyFile(requireAuthentication: Boolean) = withContext(Dispatchers.IO) {
        EncryptedFile.Builder(context,
            privateKeyFile,
            getOrCreateWrappingMasterKey(requireAuthentication),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).run {
            setKeysetPrefName(ANDROIDX_SECURITY_KEYSET_PREF_NAME)
            build()
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun generateKeystoreWrappedEd25519Key(requireAuthentication: Boolean) = withContext(Dispatchers.IO) {
        delete()

        val encryptedPrivateKeyFile = getOrCreateWrappedPrivateKeyFile(requireAuthentication)
        // Generate the ed25519 key pair and encrypt the private key.
        val keyPair = net.i2p.crypto.eddsa.KeyPairGenerator().generateKeyPair()
        encryptedPrivateKeyFile.openFileOutput().use { os ->
            os.write((keyPair.private as EdDSAPrivateKey).seed)
        }

        // Write public key in SSH format to .ssh_key.pub.
        publicKeyFile.writeText(toSshPublicKey(keyPair.public))

        type = Type.KeystoreWrappedEd25519
    }

    fun generateKeystoreNativeKey(algorithm: Algorithm, requireAuthentication: Boolean) {
        delete()

        // Generate Keystore-backed private key.
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS, KeyProperties.PURPOSE_SIGN
        ).run {
            apply(algorithm.applyToSpec)
            if (requireAuthentication) {
                setUserAuthenticationRequired(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(30, KeyProperties.AUTH_DEVICE_CREDENTIAL)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(30)
                }
            }
            build()
        }
        val keyPair = KeyPairGenerator.getInstance(algorithm.algorithm, PROVIDER_ANDROID_KEY_STORE).run {
            initialize(parameterSpec)
            generateKeyPair()
        }

        // Write public key in SSH format to .ssh_key.pub.
        publicKeyFile.writeText(toSshPublicKey(keyPair.public))

        type = Type.KeystoreNative
    }

    fun provide(client: SSHClient, passphraseFinder: InteractivePasswordFinder): KeyProvider? = when (type) {
        Type.LegacyGenerated, Type.Imported -> client.loadKeys(privateKeyFile.absolutePath, passphraseFinder)
        Type.KeystoreNative -> KeystoreNativeKeyProvider
        Type.KeystoreWrappedEd25519 -> KeystoreWrappedEd25519KeyProvider
        null -> null
    }

    private object KeystoreNativeKeyProvider : KeyProvider {

        override fun getPublic(): PublicKey = runCatching {
            androidKeystore.sshPublicKey!!
        }.getOrElse { error ->
            e(error)
            throw IOException("Failed to get public key '$KEYSTORE_ALIAS' from Android Keystore", error)
        }

        override fun getPrivate(): PrivateKey = runCatching {
            androidKeystore.sshPrivateKey!!
        }.getOrElse { error ->
            e(error)
            throw IOException("Failed to access private key '$KEYSTORE_ALIAS' from Android Keystore", error)
        }

        override fun getType(): KeyType = KeyType.fromKey(public)
    }

    private object KeystoreWrappedEd25519KeyProvider : KeyProvider {

        override fun getPublic(): PublicKey = runCatching {
            parseSshPublicKey(sshPublicKey!!)!!
        }.getOrElse { error ->
            e(error)
            throw IOException("Failed to get the public key for wrapped ed25519 key", error)
        }

        override fun getPrivate(): PrivateKey = runCatching {
            // The current MasterKey API does not allow getting a reference to an existing one
            // without specifying the KeySpec for a new one. However, the value for passed here
            // for `requireAuthentication` is not used as the key already exists at this point.
            val encryptedPrivateKeyFile = runBlocking {
                getOrCreateWrappedPrivateKeyFile(false)
            }
            val rawPrivateKey = encryptedPrivateKeyFile.openFileInput().use { it.readBytes() }
            EdDSAPrivateKey(EdDSAPrivateKeySpec(rawPrivateKey, EdDSANamedCurveTable.ED_25519_CURVE_SPEC))
        }.getOrElse { error ->
            e(error)
            throw IOException("Failed to unwrap wrapped ed25519 key", error)
        }

        override fun getType(): KeyType = KeyType.fromKey(public)
    }
}
