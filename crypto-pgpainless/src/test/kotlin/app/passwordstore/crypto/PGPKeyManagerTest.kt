package app.passwordstore.crypto

import app.passwordstore.crypto.GpgIdentifier.KeyId
import app.passwordstore.crypto.GpgIdentifier.UserId
import app.passwordstore.crypto.TestUtils.getArmoredPrivateKeyWithMultipleIdentities
import app.passwordstore.crypto.errors.KeyAlreadyExistsException
import app.passwordstore.crypto.errors.KeyNotFoundException
import app.passwordstore.crypto.errors.NoKeysAvailableException
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
  private val key = PGPKey(TestUtils.getArmoredPrivateKey())

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
      val keyId = keyManager.getKeyId(keyManager.addKey(key).unwrap())
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
      keyManager.addKey(key, false).unwrap()
      val error = keyManager.addKey(key, false).unwrapError()

      assertIs<KeyAlreadyExistsException>(error)
    }

  @Test
  fun addKeyWithReplaceFlag() =
    scope.runTest {
      // Check adding the keys twice
      keyManager.addKey(key, true).unwrap()
      val keyId = keyManager.getKeyId(keyManager.addKey(key, true).unwrap())

      assertEquals(KeyId(CryptoConstants.KEY_ID), keyId)
    }

  @Test
  fun removeKey() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(key).unwrap()

      // Check if the key id returned is correct
      val keyId = keyManager.getKeyId(keyManager.removeKey(key).unwrap())
      assertEquals(KeyId(CryptoConstants.KEY_ID), keyId)

      // Check if the keys directory have 0 files
      val keysDir = File(filesDir, PGPKeyManager.KEY_DIR_NAME)
      assertEquals(0, keysDir.list()?.size)
    }

  @Test
  fun getKeyById() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(key).unwrap()

      val keyId = keyManager.getKeyId(key)
      assertNotNull(keyId)
      assertEquals(KeyId(CryptoConstants.KEY_ID), keyManager.getKeyId(key))

      // Check returned key id matches the expected id and the created key id
      val returnedKey = keyManager.getKeyById(keyId).unwrap()
      assertEquals(keyManager.getKeyId(key), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun getKeyByFullUserId() =
    scope.runTest {
      keyManager.addKey(key).unwrap()

      val keyId = "${CryptoConstants.KEY_NAME} <${CryptoConstants.KEY_EMAIL}>"
      val returnedKey = keyManager.getKeyById(UserId(keyId)).unwrap()
      assertEquals(keyManager.getKeyId(key), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun getKeyByEmailUserId() =
    scope.runTest {
      keyManager.addKey(key).unwrap()

      val keyId = CryptoConstants.KEY_EMAIL
      val returnedKey = keyManager.getKeyById(UserId(keyId)).unwrap()
      assertEquals(keyManager.getKeyId(key), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun getNonExistentKey() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(key).unwrap()

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
      keyManager.addKey(key).unwrap()
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
}
