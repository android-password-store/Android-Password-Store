package dev.msfjarvis.aps.crypto

import com.github.michaelbull.result.unwrap
import com.github.michaelbull.result.unwrapError
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PGPKeyManagerTest {

  @get:Rule val temporaryFolder: TemporaryFolder = TemporaryFolder()
  private val filesDir by lazy(LazyThreadSafetyMode.NONE) { temporaryFolder.root }
  private val keysDir by
    lazy(LazyThreadSafetyMode.NONE) { File(filesDir, PGPKeyManager.KEY_DIR_NAME) }
  private val testCoroutineDispatcher = TestCoroutineDispatcher()
  private val keyManager by
    lazy(LazyThreadSafetyMode.NONE) {
      PGPKeyManager(filesDir.absolutePath, testCoroutineDispatcher)
    }
  private val key = PGPKeyManager.makeKey(TestUtils.getArmoredPrivateKey())

  @Test
  fun testAddingKey() {
    runBlockingTest {
      // Check if the key id returned is correct
      val keyId = keyManager.addKey(key).unwrap().getKeyId()
      assertEquals(CryptoConstants.KEY_ID, keyId)

      // Check if the keys directory have one file
      assertEquals(1, filesDir.list()?.size)

      // Check if the file name is correct
      val keyFile = keysDir.listFiles()?.first()
      assertEquals(keyFile?.name, "$keyId.${PGPKeyManager.KEY_EXTENSION}")
    }
  }

  @Test
  fun testAddingKeyWithoutReplaceFlag() {
    runBlockingTest {
      // Check adding the keys twice
      keyManager.addKey(key, false).unwrap()
      val error = keyManager.addKey(key, false).unwrapError()

      assertIs<KeyManagerException.KeyAlreadyExistsException>(error)
    }
  }

  @Test
  fun testAddingKeyWithReplaceFlag() {
    runBlockingTest {
      // Check adding the keys twice
      keyManager.addKey(key, true).unwrap()
      val keyId = keyManager.addKey(key, true).unwrap().getKeyId()

      assertEquals(CryptoConstants.KEY_ID, keyId)
    }
  }

  @Test
  fun testRemovingKey() {
    runBlockingTest {
      // Add key using KeyManager
      keyManager.addKey(key).unwrap()

      // Check if the key id returned is correct
      val keyId = keyManager.removeKey(key).unwrap().getKeyId()
      assertEquals(CryptoConstants.KEY_ID, keyId)

      // Check if the keys directory have 0 files
      val keysDir = File(filesDir, PGPKeyManager.KEY_DIR_NAME)
      assertEquals(0, keysDir.list()?.size)
    }
  }

  @Test
  fun testGetExistingKey() {
    runBlockingTest {
      // Add key using KeyManager
      keyManager.addKey(key).unwrap()

      // Check returned key id matches the expected id and the created key id
      val returnedKeyPair = keyManager.getKeyById(key.getKeyId()).unwrap()
      assertEquals(CryptoConstants.KEY_ID, key.getKeyId())
      assertEquals(key.getKeyId(), returnedKeyPair.getKeyId())
    }
  }

  @Test
  fun testGetNonExistentKey() {
    runBlockingTest {
      // Add key using KeyManager
      keyManager.addKey(key).unwrap()

      val randomKeyId = "0x123456789"

      // Check returned key
      val error = keyManager.getKeyById(randomKeyId).unwrapError()
      assertIs<KeyManagerException.KeyNotFoundException>(error)
      assertEquals("No key found with id: $randomKeyId", error.message)
    }
  }

  @Test
  fun testFindKeysWithoutAdding() {
    runBlockingTest {
      // Check returned key
      val error = keyManager.getKeyById("0x123456789").unwrapError()
      assertIs<KeyManagerException.NoKeysAvailableException>(error)
      assertEquals("No keys were found", error.message)
    }
  }

  @Test
  fun testGettingAllKeys() {
    runBlockingTest {
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
}
