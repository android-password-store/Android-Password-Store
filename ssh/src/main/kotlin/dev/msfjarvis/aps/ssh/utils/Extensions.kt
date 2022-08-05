package dev.msfjarvis.aps.ssh.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.msfjarvis.aps.ssh.utils.Constants.KEYSTORE_ALIAS
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType

// TODO: remove hardcoded application id
/** Get the default [SharedPreferences] instance */
internal val Context.sharedPrefs: SharedPreferences
  get() = getSharedPreferences("app.passwordstore_preferences", 0)
internal val KeyStore.sshPrivateKey
  get() = getKey(KEYSTORE_ALIAS, null) as? PrivateKey
internal val KeyStore.sshPublicKey
  get() = getCertificate(KEYSTORE_ALIAS)?.publicKey

internal fun String.parseStringPublicKey(): PublicKey? {
  val sshKeyParts = this.split("""\s+""".toRegex())
  if (sshKeyParts.size < 2) return null
  return Buffer.PlainBuffer(Base64.decode(sshKeyParts[1], Base64.NO_WRAP)).readPublicKey()
}

internal fun PublicKey.createStringPublicKey(): String {
  val rawPublicKey = Buffer.PlainBuffer().putPublicKey(this).compactData
  val keyType = KeyType.fromKey(this)
  return "$keyType ${Base64.encodeToString(rawPublicKey, Base64.NO_WRAP)}"
}
// TODO: make a separate utils module for common extensions
/** Wrapper for [getEncryptedPrefs] to avoid open-coding the file name at each call site */
internal fun Context.getEncryptedGitPrefs() = getEncryptedPrefs("git_operation")

/** Get an instance of [EncryptedSharedPreferences] with the given [fileName] */
private fun Context.getEncryptedPrefs(fileName: String): SharedPreferences {
  val masterKeyAlias =
    MasterKey.Builder(applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
  return EncryptedSharedPreferences.create(
    applicationContext,
    fileName,
    masterKeyAlias,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
  )
}
