package app.passwordstore.crypto

import app.passwordstore.crypto.KeyUtils.tryGetId
import app.passwordstore.crypto.KeyUtils.tryParseKeyring
import app.passwordstore.crypto.TestUtils.getArmoredSecretKeyWithMultipleIdentities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.bouncycastle.openpgp.PGPSecretKeyRing

class KeyUtilsTest {
  @Test
  fun parseKeyWithMultipleIdentities() {
    val key = PGPKey(getArmoredSecretKeyWithMultipleIdentities())
    val keyring = tryParseKeyring(key)
    assertNotNull(keyring)
    assertIs<PGPSecretKeyRing>(keyring)
    val keyId = tryGetId(key)
    assertNotNull(keyId)
    assertIs<GpgIdentifier.KeyId>(keyId)
    assertEquals("b950ae2813841585", keyId.toString())
  }
}
