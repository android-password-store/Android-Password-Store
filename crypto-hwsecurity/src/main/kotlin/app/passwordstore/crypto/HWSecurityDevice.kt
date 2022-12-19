package app.passwordstore.crypto

import de.cotech.hw.openpgp.OpenPgpSecurityKey
import de.cotech.hw.openpgp.internal.openpgp.EcKeyFormat
import de.cotech.hw.openpgp.internal.openpgp.KeyFormat
import de.cotech.hw.openpgp.internal.openpgp.RsaKeyFormat
import org.pgpainless.algorithm.PublicKeyAlgorithm
import org.pgpainless.key.OpenPgpFingerprint

public class HWSecurityDevice(
  public val id: DeviceIdentifier,
  public val name: String,
  public val encryptKeyInfo: DeviceKeyInfo?,
  public val signKeyInfo: DeviceKeyInfo?,
  public val authKeyInfo: DeviceKeyInfo?,
)

internal fun OpenPgpSecurityKey.toDevice(): HWSecurityDevice =
  with(openPgpAppletConnection.openPgpCapabilities) {
    HWSecurityDevice(
      id = DeviceIdentifier(aid),
      name = securityKeyName,
      encryptKeyInfo = keyInfo(encryptKeyFormat, fingerprintEncrypt),
      signKeyInfo = keyInfo(signKeyFormat, fingerprintSign),
      authKeyInfo = keyInfo(authKeyFormat, fingerprintAuth)
    )
  }

internal fun keyInfo(format: KeyFormat?, fingerprint: ByteArray?): DeviceKeyInfo? {
  if (format == null || fingerprint == null) return null
  return DeviceKeyInfo(format.toKeyAlgorithm(), OpenPgpFingerprint.parseFromBinary(fingerprint))
}

internal fun KeyFormat.toKeyAlgorithm(): PublicKeyAlgorithm =
  when (this) {
    is RsaKeyFormat -> PublicKeyAlgorithm.RSA_GENERAL
    is EcKeyFormat ->
      when (val id = algorithmId()) {
        PublicKeyAlgorithm.ECDH.algorithmId -> PublicKeyAlgorithm.ECDH
        PublicKeyAlgorithm.ECDSA.algorithmId -> PublicKeyAlgorithm.ECDSA
        PublicKeyAlgorithm.EDDSA.algorithmId -> PublicKeyAlgorithm.EDDSA
        else -> throw IllegalArgumentException("Unknown EC algorithm ID: $id")
      }
    else -> throw IllegalArgumentException("Unknown key format")
  }
