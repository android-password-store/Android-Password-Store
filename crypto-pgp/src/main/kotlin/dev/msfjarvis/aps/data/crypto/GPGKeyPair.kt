package dev.msfjarvis.aps.data.crypto

import com.proton.Gopenpgp.crypto.Key

/** Wraps a Gopenpgp [Key] to implement [KeyPair]. */
public class GPGKeyPair(private val key: Key) : KeyPair {

  override fun getPrivateKey(): ByteArray {
    if (!key.isPrivate) error("GPGKeyPair does not have a private sub key")

    return key.armor().encodeToByteArray()
  }

  override fun getPublicKey(): ByteArray {
    return key.armoredPublicKey.encodeToByteArray()
  }

  override fun getKeyId(): String {
    return key.hexKeyID
  }
}