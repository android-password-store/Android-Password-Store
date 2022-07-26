package dev.msfjarvis.aps.ssh.writer

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dev.msfjarvis.aps.ssh.utils.Constants.ANDROIDX_SECURITY_KEYSET_PREF_NAME
import dev.msfjarvis.aps.ssh.utils.Constants.KEYSTORE_ALIAS
import dev.msfjarvis.aps.ssh.utils.createStringPublicKey
import java.io.File
import java.security.PrivateKey
import java.security.PublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.i2p.crypto.eddsa.EdDSAPrivateKey

public class ED25519KeyWriter(
  private val context: Context,
  private val requiresAuthentication: Boolean,
) : SSHKeyWriter {

  override suspend fun writePrivateKey(privateKey: PrivateKey, privateKeyFile: File) {
    withContext(Dispatchers.IO) {
      val encryptedPrivateKeyFile =
        getOrCreateWrappedPrivateKeyFile(requiresAuthentication, privateKeyFile)
      encryptedPrivateKeyFile.openFileOutput().use { os ->
        os.write((privateKey as EdDSAPrivateKey).seed)
      }
    }
  }

  override suspend fun writePublicKey(publicKey: PublicKey, publicKeyFile: File) {
    publicKeyFile.writeText(publicKey.createStringPublicKey())
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun getOrCreateWrappedPrivateKeyFile(
    requiresAuthentication: Boolean,
    privateKeyFile: File
  ) =
    withContext(Dispatchers.IO) {
      EncryptedFile.Builder(
          context,
          privateKeyFile,
          getOrCreateWrappingMasterKey(requiresAuthentication),
          EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        )
        .run {
          setKeysetPrefName(ANDROIDX_SECURITY_KEYSET_PREF_NAME)
          build()
        }
    }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun getOrCreateWrappingMasterKey(requireAuthentication: Boolean) =
    withContext(Dispatchers.IO) {
      MasterKey.Builder(context, KEYSTORE_ALIAS).run {
        setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        setRequestStrongBoxBacked(true)
        setUserAuthenticationRequired(requireAuthentication, 15)
        build()
      }
    }
}
