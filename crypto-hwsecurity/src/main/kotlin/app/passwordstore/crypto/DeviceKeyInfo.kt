package app.passwordstore.crypto

import org.pgpainless.algorithm.PublicKeyAlgorithm
import org.pgpainless.key.OpenPgpFingerprint

public data class DeviceKeyInfo(
    public val algorithm: PublicKeyAlgorithm,
    public val fingerprint: OpenPgpFingerprint
) {
    override fun toString(): String = "${algorithm.displayName()} ${fingerprint.prettyPrint()}"
}

@Suppress("DEPRECATION")
private fun PublicKeyAlgorithm.displayName(): String = when (this) {
    PublicKeyAlgorithm.RSA_GENERAL -> "RSA"
    PublicKeyAlgorithm.RSA_ENCRYPT -> "RSA (encrypt-only, deprecated)"
    PublicKeyAlgorithm.RSA_SIGN -> "RSA (sign-only, deprecated)"
    PublicKeyAlgorithm.ELGAMAL_ENCRYPT -> "ElGamal"
    PublicKeyAlgorithm.DSA -> "DSA"
    PublicKeyAlgorithm.EC -> "EC (deprecated)"
    PublicKeyAlgorithm.ECDH -> "ECDH"
    PublicKeyAlgorithm.ECDSA -> "ECDSA"
    PublicKeyAlgorithm.ELGAMAL_GENERAL -> "ElGamal (general, deprecated)"
    PublicKeyAlgorithm.DIFFIE_HELLMAN -> "Diffie-Hellman"
    PublicKeyAlgorithm.EDDSA -> "EDDSA"
}
