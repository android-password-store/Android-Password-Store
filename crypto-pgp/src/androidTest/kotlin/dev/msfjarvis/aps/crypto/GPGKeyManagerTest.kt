package dev.msfjarvis.aps.crypto

import androidx.test.platform.app.InstrumentationRegistry
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.unwrap
import com.proton.Gopenpgp.crypto.Key
import dev.msfjarvis.aps.crypto.utils.CryptoConstants
import dev.msfjarvis.aps.cryptopgp.test.R
import dev.msfjarvis.aps.data.crypto.GPGKeyManager
import dev.msfjarvis.aps.data.crypto.GPGKeyPair
import java.io.File
import java.lang.IllegalStateException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
public class GPGKeyManagerTest {

  private val testCoroutineDispatcher = TestCoroutineDispatcher()
  private lateinit var gpgKeyManager: GPGKeyManager
  private lateinit var key: GPGKeyPair

  @Before
  public fun setup() {
    gpgKeyManager = GPGKeyManager(getFilesDir().absolutePath, testCoroutineDispatcher)
    key = GPGKeyPair(Key(getKey()))
  }

  @After
  public fun tearDown() {
    val filesDir = getFilesDir()
    val keysDir = File(filesDir, GPGKeyManager.KEY_DIR_NAME)

    keysDir.deleteRecursively()
  }

  @Test
  public fun testAddingKey() {
    runBlockingTest {
      // Check if the key id returned is correct
      val keyId = gpgKeyManager.addKey(key).unwrap()
      assertEquals(CryptoConstants.KEY_ID, keyId)

      // Check if the keys directory have one file
      val keysDir = File(getFilesDir(), GPGKeyManager.KEY_DIR_NAME)
      assertEquals(1, keysDir.list()?.size)

      // Check if the file name is correct
      val keyFile = keysDir.listFiles()?.first()
      assertEquals(keyFile?.name, "$keyId.${GPGKeyManager.KEY_EXTENSION}")
    }
  }

  @Test
  public fun testRemovingKey() {
    runBlockingTest {
      // Add key using KeyManager
      gpgKeyManager.addKey(key).unwrap()

      // Check if the key id returned is correct
      val keyId = gpgKeyManager.removeKey(key).unwrap()
      assertEquals(CryptoConstants.KEY_ID, keyId)

      // Check if the keys directory have 0 files
      val keysDir = File(getFilesDir(), GPGKeyManager.KEY_DIR_NAME)
      assertEquals(0, keysDir.list()?.size)
    }
  }

  @Test
  public fun testFindingKeyWhenKeyIsAvailable() {
    runBlockingTest {
      // Add key using KeyManager
      gpgKeyManager.addKey(key).unwrap()

      // Check returned key id matches the expected id and the created key id
      val returnedKeyPair = gpgKeyManager.getKeyById(key.getKeyId()).unwrap()
      assertEquals(CryptoConstants.KEY_ID, key.getKeyId())
      assertEquals(key.getKeyId(), returnedKeyPair.getKeyId())
    }
  }

  @Test
  public fun testGetNonExistentKey() {
    runBlockingTest {
      // Add key using KeyManager
      gpgKeyManager.addKey(key).unwrap()

      val randomKeyId = "0x123456789"

      // Check returned key
      val error = gpgKeyManager.getKeyById(randomKeyId).getError()
      assertIs<IllegalStateException>(error)
      assertEquals("No key found with id: $randomKeyId", error.message)
    }
  }

  @Test
  public fun testFindKeysWithoutAdding() {
    runBlockingTest {
      // Check returned key
      val error = gpgKeyManager.getKeyById("0x123456789").getError()
      assertIs<IllegalStateException>(error)
      assertEquals("No keys were found", error.message)
    }
  }

  @Test
  public fun testGettingAllKeys() {
    runBlockingTest {
      // TODO: Should we check for more than 1 keys?
      // Check if KeyManager returns no key
      val noKeyList = gpgKeyManager.getAllKeys().unwrap()
      assertEquals(0, noKeyList.size)

      // Add key using KeyManager
      gpgKeyManager.addKey(key).unwrap()

      // Check if KeyManager returns one key
      val singleKeyList = gpgKeyManager.getAllKeys().unwrap()
      assertEquals(1, singleKeyList.size)
    }
  }

  private companion object {

    fun getFilesDir(): File = InstrumentationRegistry.getInstrumentation().context.filesDir

    fun getKey(): String =
      InstrumentationRegistry.getInstrumentation()
        .context
        .resources
        .openRawResource(R.raw.private_key)
        .readBytes()
        .decodeToString()
  }
}
