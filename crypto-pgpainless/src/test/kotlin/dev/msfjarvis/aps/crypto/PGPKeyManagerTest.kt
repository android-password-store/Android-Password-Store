package dev.msfjarvis.aps.crypto

import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
  fun testAddingKey() =
    scope.runTest {
      // Check if the key id returned is correct
      val keyId = keyManager.getKeyId(keyManager.addKey(key).unwrap())
      assertEquals(CryptoConstants.KEY_ID, keyId)

      // Check if the keys directory have one file
      assertEquals(1, filesDir.list()?.size)

      // Check if the file name is correct
      val keyFile = keysDir.listFiles()?.first()
      assertEquals(keyFile?.name, "$keyId.${PGPKeyManager.KEY_EXTENSION}")
    }

  @Test
  fun testAddingKeyWithoutReplaceFlag() =
    scope.runTest {
      // Check adding the keys twice
      keyManager.addKey(key, false).unwrap()
      val error = keyManager.addKey(key, false).unwrapError()

      assertIs<KeyManagerException.KeyAlreadyExistsException>(error)
    }

  @Test
  fun testAddingKeyWithReplaceFlag() =
    scope.runTest {
      // Check adding the keys twice
      keyManager.addKey(key, true).unwrap()
      val keyId = keyManager.getKeyId(keyManager.addKey(key, true).unwrap())

      assertEquals(CryptoConstants.KEY_ID, keyId)
    }

  @Test
  fun testRemovingKey() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(key).unwrap()

      // Check if the key id returned is correct
      val keyId = keyManager.getKeyId(keyManager.removeKey(key).unwrap())
      assertEquals(CryptoConstants.KEY_ID, keyId)

      // Check if the keys directory have 0 files
      val keysDir = File(filesDir, PGPKeyManager.KEY_DIR_NAME)
      assertEquals(0, keysDir.list()?.size)
    }

  @Test
  fun testGetExistingKeyById() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(key).unwrap()

      val keyId = keyManager.getKeyId(key)
      assertNotNull(keyId)
      assertEquals(CryptoConstants.KEY_ID, keyManager.getKeyId(key))

      // Check returned key id matches the expected id and the created key id
      val returnedKey = keyManager.getKeyById(keyId).unwrap()
      assertEquals(keyManager.getKeyId(key), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun testGetExistingKeyByFullUserId() =
    scope.runTest {
      keyManager.addKey(key).unwrap()

      val keyId = "${CryptoConstants.KEY_NAME} <${CryptoConstants.KEY_EMAIL}>"
      val returnedKey = keyManager.getKeyById(keyId).unwrap()
      assertEquals(keyManager.getKeyId(key), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun testGetExistingKeyByEmailUserId() =
    scope.runTest {
      keyManager.addKey(key).unwrap()

      val keyId = CryptoConstants.KEY_EMAIL
      val returnedKey = keyManager.getKeyById(keyId).unwrap()
      assertEquals(keyManager.getKeyId(key), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun testGetNonExistentKey() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(key).unwrap()

      val randomKeyId = "0x123456789"

      // Check returned key
      val error = keyManager.getKeyById(randomKeyId).unwrapError()
      assertIs<KeyManagerException.KeyNotFoundException>(error)
      assertEquals("No key found with id: $randomKeyId", error.message)
    }

  @Test
  fun testFindKeysWithoutAdding() =
    scope.runTest {
      // Check returned key
      val error = keyManager.getKeyById("0x123456789").unwrapError()
      assertIs<KeyManagerException.NoKeysAvailableException>(error)
      assertEquals("No keys were found", error.message)
    }

  @Test
  fun testGettingAllKeys() =
    scope.runTest {
      // TODO: Should we check for more than 1 keys?
      // Check if KeyManager returns no key
      val noKeyList = keyManager.getAllKeys().unwrap()
      assertEquals(0, noKeyList.size)

      // Add key using KeyManager
      keyManager.addKey(key).unwrap()

      // Check if KeyManager returns one key
      val singleKeyList = keyManager.getAllKeys().unwrap()
      assertEquals(1, singleKeyList.size)
    }
}
