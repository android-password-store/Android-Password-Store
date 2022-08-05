package dev.msfjarvis.aps.ssh.utils

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object SSHKeyUtils {

  fun isValid(lines: List<String>): Boolean {
    return lines.size > 2 &&
      !Regex("BEGIN .* PRIVATE KEY").containsMatchIn(lines.first()) &&
      !Regex("END .* PRIVATE KEY").containsMatchIn(lines.last())
  }

  @Suppress("BlockingMethodInNonBlockingContext")
  suspend fun getOrCreateWrappedPrivateKeyFile(
    context: Context,
    requiresAuthentication: Boolean,
    privateKeyFile: File
  ) =
    withContext(Dispatchers.IO) {
      EncryptedFile.Builder(
          context,
          privateKeyFile,
          getOrCreateWrappingMasterKey(context, requiresAuthentication),
          EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        )
        .run {
          setKeysetPrefName(Constants.ANDROIDX_SECURITY_KEYSET_PREF_NAME)
          build()
        }
    }

  @Suppress("BlockingMethodInNonBlockingContext")
  private suspend fun getOrCreateWrappingMasterKey(
    context: Context,
    requireAuthentication: Boolean
  ) =
    withContext(Dispatchers.IO) {
      MasterKey.Builder(context, Constants.KEYSTORE_ALIAS).run {
        setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        setRequestStrongBoxBacked(true)
        setUserAuthenticationRequired(requireAuthentication, 15)
        build()
      }
    }
}
