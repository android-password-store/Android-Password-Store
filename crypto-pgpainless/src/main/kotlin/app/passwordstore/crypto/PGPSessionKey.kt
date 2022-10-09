package app.passwordstore.crypto

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSessionKey
import org.pgpainless.algorithm.PublicKeyAlgorithm

public class PGPEncryptedSessionKey(
  public val publicKey: PGPPublicKey,
  public val algorithm: PublicKeyAlgorithm,
  public val contents: ByteArray
)

public fun PGPSessionKey(
  algorithm: PublicKeyAlgorithm,
  sessionKey: ByteArray
): PGPSessionKey = PGPSessionKey(algorithm.algorithmId, sessionKey)
