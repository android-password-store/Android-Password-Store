package app.passwordstore.crypto

import app.passwordstore.crypto.GpgIdentifier.KeyId
import app.passwordstore.crypto.GpgIdentifier.UserId
import app.passwordstore.crypto.TestUtils.getArmoredPrivateKeyWithMultipleIdentities
import app.passwordstore.crypto.errors.KeyAlreadyExistsException
import app.passwordstore.crypto.errors.KeyNotFoundException
import app.passwordstore.crypto.errors.NoKeysAvailableException
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PGPKeyManagerTest {

  @get:Rule val temporaryFolder: TemporaryFolder = TemporaryFolder()
  private val filesDir by unsafeLazy { temporaryFolder.root }
  private val keysDir by unsafeLazy { File(filesDir, PGPKeyManager.KEY_DIR_NAME) }
  private val dispatcher = StandardTestDispatcher()
  private val scope = TestScope(dispatcher)
  private val keyManager by unsafeLazy { PGPKeyManager(filesDir.absolutePath, dispatcher) }
  private val privateKey = PGPKey(TestUtils.getArmoredPrivateKey())
  private val publicKey = PGPKey(TestUtils.getArmoredPublicKey())

  private fun <T> unsafeLazy(initializer: () -> T) =
    lazy(LazyThreadSafetyMode.NONE) { initializer.invoke() }

  @BeforeTest
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun addKey() =
    scope.runTest {
      // Check if the key id returned is correct
      val keyId = keyManager.getKeyId(keyManager.addKey(privateKey).unwrap())
      assertEquals(KeyId(CryptoConstants.KEY_ID), keyId)

      // Check if the keys directory have one file
      assertEquals(1, filesDir.list()?.size)

      // Check if the file name is correct
      val keyFile = keysDir.listFiles()?.first()
      assertEquals(keyFile?.name, "$keyId.${PGPKeyManager.KEY_EXTENSION}")
    }

  @Test
  fun addKeyWithoutReplaceFlag() =
    scope.runTest {
      // Check adding the keys twice
      keyManager.addKey(privateKey, false).unwrap()
      val error = keyManager.addKey(privateKey, false).unwrapError()

      assertIs<KeyAlreadyExistsException>(error)
    }

  @Test
  fun addKeyWithReplaceFlag() =
    scope.runTest {
      // Check adding the keys twice
      keyManager.addKey(privateKey, true).unwrap()
      val keyId = keyManager.getKeyId(keyManager.addKey(privateKey, true).unwrap())

      assertEquals(KeyId(CryptoConstants.KEY_ID), keyId)
    }

  @Test
  fun removeKey() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(privateKey).unwrap()

      // Check if the key id returned is correct
      val keyId = keyManager.getKeyId(keyManager.removeKey(privateKey).unwrap())
      assertEquals(KeyId(CryptoConstants.KEY_ID), keyId)

      // Check if the keys directory have 0 files
      val keysDir = File(filesDir, PGPKeyManager.KEY_DIR_NAME)
      assertEquals(0, keysDir.list()?.size)
    }

  @Test
  fun getKeyById() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(privateKey).unwrap()

      val keyId = keyManager.getKeyId(privateKey)
      assertNotNull(keyId)
      assertEquals(KeyId(CryptoConstants.KEY_ID), keyManager.getKeyId(privateKey))

      // Check returned key id matches the expected id and the created key id
      val returnedKey = keyManager.getKeyById(keyId).unwrap()
      assertEquals(keyManager.getKeyId(privateKey), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun getKeyByFullUserId() =
    scope.runTest {
      keyManager.addKey(privateKey).unwrap()

      val keyId = "${CryptoConstants.KEY_NAME} <${CryptoConstants.KEY_EMAIL}>"
      val returnedKey = keyManager.getKeyById(UserId(keyId)).unwrap()
      assertEquals(keyManager.getKeyId(privateKey), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun getKeyByEmailUserId() =
    scope.runTest {
      keyManager.addKey(privateKey).unwrap()

      val keyId = CryptoConstants.KEY_EMAIL
      val returnedKey = keyManager.getKeyById(UserId(keyId)).unwrap()
      assertEquals(keyManager.getKeyId(privateKey), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun getNonExistentKey() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(privateKey).unwrap()

      val keyId = KeyId(0x08edf7567183ce44)

      // Check returned key
      val error = keyManager.getKeyById(keyId).unwrapError()
      assertIs<KeyNotFoundException>(error)
      assertEquals("No key found with id: $keyId", error.message)
    }

  @Test
  fun findNonExistentKey() =
    scope.runTest {
      // Check returned key
      val error = keyManager.getKeyById(KeyId(0x08edf7567183ce44)).unwrapError()
      assertIs<NoKeysAvailableException>(error)
      assertEquals("No keys were found", error.message)
    }

  @Test
  fun getAllKeys() =
    scope.runTest {
      // Check if KeyManager returns no key
      val noKeyList = keyManager.getAllKeys().unwrap()
      assertEquals(0, noKeyList.size)

      // Add key using KeyManager
      keyManager.addKey(privateKey).unwrap()
      keyManager.addKey(PGPKey(getArmoredPrivateKeyWithMultipleIdentities())).unwrap()

      // Check if KeyManager returns one key
      val singleKeyList = keyManager.getAllKeys().unwrap()
      assertEquals(2, singleKeyList.size)
    }

  @Test
  fun getMultipleIdentityKeyWithAllIdentities() {
    scope.runTest {
      val key = PGPKey(getArmoredPrivateKeyWithMultipleIdentities())
      keyManager.addKey(key).unwrap()

      val johnKey = keyManager.getKeyById(UserId("john@doe.org")).unwrap()
      val janeKey = keyManager.getKeyById(UserId("jane@doe.org")).unwrap()

      assertContentEquals(johnKey.contents, janeKey.contents)
    }
  }

  @Test
  fun replacePrivateKeyWithPublicKey() {
    scope.runTest {
      assertIs<Ok<PGPKey>>(keyManager.addKey(privateKey))
      assertIs<Err<KeyAlreadyExistsException>>(keyManager.addKey(publicKey))
    }
  }

  @Test
  fun replacePublicKeyWithSecretKey() {
    scope.runTest {
      assertIs<Ok<PGPKey>>(keyManager.addKey(publicKey))
      assertIs<Ok<PGPKey>>(keyManager.addKey(privateKey))
    }
  }

  @Test
  fun replacePublicKeyWithPublicKey() {
    scope.runTest {
      assertIs<Ok<PGPKey>>(keyManager.addKey(publicKey))
      assertIs<Ok<PGPKey>>(keyManager.addKey(publicKey))
      val allKeys = keyManager.getAllKeys()
      assertIs<Ok<List<PGPKey>>>(allKeys)
      assertEquals(1, allKeys.value.size)
      val key = allKeys.value[0]
      assertContentEquals(publicKey.contents, key.contents)
    }
  }

  @Test
  fun replaceSecretKeyWithSecretKey() {
    scope.runTest {
      assertIs<Ok<PGPKey>>(keyManager.addKey(privateKey))
      assertIs<Err<KeyAlreadyExistsException>>(keyManager.addKey(privateKey))
    }
  }
}
