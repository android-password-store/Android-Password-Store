package dev.msfjarvis.aps.ssh

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.security.keystore.KeyInfo
import androidx.core.content.edit
import dev.msfjarvis.aps.ssh.generator.ECDSAKeyGenerator
import dev.msfjarvis.aps.ssh.generator.ED25519KeyGenerator
import dev.msfjarvis.aps.ssh.generator.RSAKeyGenerator
import dev.msfjarvis.aps.ssh.provider.KeystoreNativeKeyProvider
import dev.msfjarvis.aps.ssh.provider.KeystoreWrappedEd25519KeyProvider
import dev.msfjarvis.aps.ssh.utils.Constants
import dev.msfjarvis.aps.ssh.utils.Constants.ANDROIDX_SECURITY_KEYSET_PREF_NAME
import dev.msfjarvis.aps.ssh.utils.Constants.KEYSTORE_ALIAS
import dev.msfjarvis.aps.ssh.utils.Constants.PROVIDER_ANDROID_KEY_STORE
import dev.msfjarvis.aps.ssh.utils.SSHKeyUtils
import dev.msfjarvis.aps.ssh.utils.getEncryptedGitPrefs
import dev.msfjarvis.aps.ssh.utils.sharedPrefs
import dev.msfjarvis.aps.ssh.writer.ED25519KeyWriter
import dev.msfjarvis.aps.ssh.writer.ImportedKeyWriter
import dev.msfjarvis.aps.ssh.writer.KeystoreNativeKeyWriter
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import logcat.asLog
import logcat.logcat
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.password.PasswordFinder

public class SSHKeyManager(private val applicationContext: Context) {

  // TODO: start using unsafeLazy here
  private val androidKeystore: KeyStore by
    lazy(LazyThreadSafetyMode.NONE) {
      KeyStore.getInstance(PROVIDER_ANDROID_KEY_STORE).apply { load(null) }
    }
  private val isStrongBoxSupported by
    lazy(LazyThreadSafetyMode.NONE) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        applicationContext.packageManager.hasSystemFeature(
          PackageManager.FEATURE_STRONGBOX_KEYSTORE
        )
      else false
    }

  // Let's make this suspend so that we can use datastore's non-blocking apis
  public suspend fun keyType(): SSHKeyType {
    // TODO: throw a custom exception here
    return SSHKeyType.fromValue(
      applicationContext.sharedPrefs.getString(Constants.GIT_REMOTE_KEY_TYPE, null)
    )
      ?: throw IllegalStateException("keyType was null")
  }

  public suspend fun keyExists(): Boolean {
    // TODO: use run suspend catching here?
    return try {
      keyType()
      true
    } catch (e: IllegalStateException) {
      false
    }
  }

  public suspend fun canShowPublicKey(): Boolean = runCatching { keyType() in listOf(SSHKeyType.LegacyGenerated, SSHKeyType.KeystoreNative, SSHKeyType.KeystoreWrappedEd25519) }.getOrElse { false }

  public suspend fun publicKey(): String? = runCatching { createNewSSHKey(keyType = keyType()).publicKey.readText() }.getOrElse { return null }

  public suspend fun needsAuthentication(): Boolean {
    return runCatching {
      val keyType = keyType()
      if (keyType == SSHKeyType.KeystoreNative || keyType == SSHKeyType.KeystoreWrappedEd25519) return false

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
    }
      .getOrElse { error ->
        // It is fine to swallow the exception here since it will reappear when the key
        // is used for SSH authentication and can then be shown in the UI.
        logcat { error.asLog() }
        false
      }
  }

  public suspend fun importKey(uri: Uri) {
    // First check whether the content at uri is likely an SSH private key.
    val fileSize =
      applicationContext.contentResolver
        .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
          // Cursor returns only a single row.
          cursor.moveToFirst()
          cursor.getInt(0)
        }
        ?: throw IOException(applicationContext.getString(R.string.ssh_key_does_not_exist))

    // We assume that an SSH key's ideal size is > 0 bytes && < 100 kilobytes.
    if (fileSize > 100_000 || fileSize == 0)
      throw IllegalArgumentException(
        applicationContext.getString(R.string.ssh_key_import_error_not_an_ssh_key_message)
      )

    val sshKeyInputStream =
      applicationContext.contentResolver.openInputStream(uri)
        ?: throw IOException(applicationContext.getString(R.string.ssh_key_does_not_exist))

    importKey(sshKeyInputStream)
  }

  public suspend fun importKey(sshKeyInputStream: InputStream) {
    val lines = sshKeyInputStream.bufferedReader().readLines()
    // The file must have more than 2 lines, and the first and last line must have private key
    // markers.
    if (!SSHKeyUtils.isValid(lines))
      throw IllegalArgumentException(
        applicationContext.getString(R.string.ssh_key_import_error_not_an_ssh_key_message)
      )
    // At this point, we are reasonably confident that we have actually been provided a private
    // key and delete the old key.
    deleteKey()

    val sshKey = createNewSSHKey(keyType = SSHKeyType.Imported)
    saveImportedKey(lines.joinToString("\n"), sshKey)
  }

  public suspend fun generateKey(algorithm: SSHKeyAlgorithm, requiresAuthentication: Boolean) {
    deleteKey()
    val (sshKeyGenerator, sshKeyType) =
      when (algorithm) {
        SSHKeyAlgorithm.RSA -> Pair(RSAKeyGenerator(), SSHKeyType.KeystoreNative)
        SSHKeyAlgorithm.ECDSA ->
          Pair(ECDSAKeyGenerator(isStrongBoxSupported), SSHKeyType.KeystoreNative)
        SSHKeyAlgorithm.ED25519 -> Pair(ED25519KeyGenerator(), SSHKeyType.KeystoreWrappedEd25519)
      }

    val keyPair = sshKeyGenerator.generateKey(requiresAuthentication)
    val sshKeyFile = createNewSSHKey(keyType = sshKeyType)
    saveGeneratedKey(keyPair, sshKeyFile, requiresAuthentication)
  }

  private suspend fun saveGeneratedKey(
    keyPair: KeyPair,
    sshKey: SSHKey,
    requiresAuthentication: Boolean
  ) {
    val sshKeyWriter =
      when (sshKey.type) {
        SSHKeyType.Imported ->
          throw UnsupportedOperationException("KeyType imported is not supported with a KeyPair")
        SSHKeyType.KeystoreNative -> KeystoreNativeKeyWriter()
        SSHKeyType.KeystoreWrappedEd25519 ->
          ED25519KeyWriter(applicationContext, requiresAuthentication)
        SSHKeyType.LegacyGenerated ->
          error("saveGeneratedKey should not be called with a legacy generated key")
      }

    sshKeyWriter.writeKeyPair(keyPair, sshKey)
    setSSHKeyType(sshKey.type)
  }

  private suspend fun saveImportedKey(key: String, sshKey: SSHKey) {
    val sshKeyWriter =
      when (sshKey.type) {
        SSHKeyType.Imported -> ImportedKeyWriter(key)
        SSHKeyType.KeystoreNative ->
          throw UnsupportedOperationException(
            "KeyType KeystoreNative is not supported with a string key"
          )
        SSHKeyType.KeystoreWrappedEd25519 ->
          throw UnsupportedOperationException(
            "KeyType KeystoreWrappedEd25519 is not supported with a string key"
          )
        SSHKeyType.LegacyGenerated ->
          error("saveImportedKey should not be called with a legacy generated key")
      }

    sshKeyWriter.writeKeyPair(KeyPair(null, null), sshKey)
    setSSHKeyType(SSHKeyType.Imported)
  }

  public suspend fun deleteKey() {
    androidKeystore.deleteEntry(KEYSTORE_ALIAS)
    // Remove Tink key set used by AndroidX's EncryptedFile.
    applicationContext
      .getSharedPreferences(ANDROIDX_SECURITY_KEYSET_PREF_NAME, Context.MODE_PRIVATE)
      .edit { clear() }

    // If there's no keyType(), we'll just use SSHKeyType.Imported, since they key is going to be deleted, it does not really matter what the key type is.
    // The other way to handle this is to return if the keyType() throws an exception.
    val sshKey = kotlin.runCatching { createNewSSHKey(keyType = keyType()) }.getOrElse { createNewSSHKey(keyType = SSHKeyType.Imported) }
    if (sshKey.privateKey.isFile) {
      sshKey.privateKey.delete()
    }
    if (sshKey.publicKey.isFile) {
      sshKey.publicKey.delete()
    }

    clearSSHKeyPreferences()
  }

  public suspend fun keyProvider(
    client: SSHClient,
    passphraseFinder: PasswordFinder
  ): KeyProvider? {
    val sshKeyFile =
      kotlin
        .runCatching { createNewSSHKey(keyType = keyType()) }
        .getOrElse {
          return null
        }
    return when (sshKeyFile.type) {
      SSHKeyType.LegacyGenerated,
      SSHKeyType.Imported -> client.loadKeys(sshKeyFile.privateKey.absolutePath, passphraseFinder)
      SSHKeyType.KeystoreNative -> KeystoreNativeKeyProvider(androidKeystore)
      SSHKeyType.KeystoreWrappedEd25519 ->
        KeystoreWrappedEd25519KeyProvider(applicationContext, sshKeyFile)
    }
  }

  private fun setSSHKeyType(sshKeyType: SSHKeyType) {
    applicationContext.sharedPrefs.edit {
      putString(Constants.GIT_REMOTE_KEY_TYPE, sshKeyType.value)
    }
  }

  private fun clearSSHKeyPreferences() {
    applicationContext.getEncryptedGitPrefs().edit { remove(Constants.SSH_KEY_LOCAL_PASSPHRASE) }
    applicationContext.sharedPrefs.edit { remove(Constants.GIT_REMOTE_KEY_TYPE) }
  }

  private fun createNewSSHKey(
    keyType: SSHKeyType,
    privateKeyFileName: String = Constants.PRIVATE_SSH_KEY_FILE_NAME,
    publicKeyFileName: String = Constants.PUBLIC_SSH_KEY_FILE_NAME
  ): SSHKey {
    val privateKeyFile = File(applicationContext.filesDir, privateKeyFileName)
    val publicKeyFile = File(applicationContext.filesDir, publicKeyFileName)

    return SSHKey(privateKeyFile, publicKeyFile, keyType)
  }
}
