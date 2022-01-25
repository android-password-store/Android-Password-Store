package dev.msfjarvis.aps.ssh.utils

import android.util.Base64
import java.security.PublicKey
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType

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
