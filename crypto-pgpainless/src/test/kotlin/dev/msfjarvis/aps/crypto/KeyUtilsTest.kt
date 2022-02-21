package dev.msfjarvis.aps.crypto

import dev.msfjarvis.aps.crypto.KeyUtils.tryGetId
import dev.msfjarvis.aps.crypto.KeyUtils.tryParseKeyring
import dev.msfjarvis.aps.crypto.TestUtils.getArmoredPrivateKeyWithMultipleIdentities
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.bouncycastle.openpgp.PGPSecretKeyRing

class KeyUtilsTest {
  @Test
  fun `parse GPG key with multiple identities`() {
    val key = PGPKey(getArmoredPrivateKeyWithMultipleIdentities())
    val keyring = tryParseKeyring(key)
    assertNotNull(keyring)
    assertIs<PGPSecretKeyRing>(keyring)
    val keyId = tryGetId(key)
    assertNotNull(keyId)
    assertIs<GpgIdentifier.KeyId>(keyId)
  }
}
