package app.passwordstore.crypto

import app.passwordstore.crypto.GpgIdentifier.KeyId
import app.passwordstore.crypto.GpgIdentifier.UserId
import app.passwordstore.crypto.KeyUtils.tryGetId
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
import kotlin.test.assertNotEquals
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
  private val dispatcher = StandardTestDispatcher()
  private val scope = TestScope(dispatcher)
  private val filesDir by unsafeLazy { temporaryFolder.root }
  private val keysDir by unsafeLazy { File(filesDir, PGPKeyManager.KEY_DIR_NAME) }
  private val keyManager by unsafeLazy { PGPKeyManager(filesDir.absolutePath, dispatcher) }
  private val secretKey = PGPKey(TestUtils.getArmoredSecretKey())
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
      val keyId = keyManager.getKeyId(keyManager.addKey(secretKey).unwrap())
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
      keyManager.addKey(secretKey, false).unwrap()
      val error = keyManager.addKey(secretKey, false).unwrapError()

      assertIs<KeyAlreadyExistsException>(error)
    }

  @Test
  fun addKeyWithReplaceFlag() =
    scope.runTest {
      // Check adding the keys twice
      keyManager.addKey(secretKey, true).unwrap()
      val keyId = keyManager.getKeyId(keyManager.addKey(secretKey, true).unwrap())

      assertEquals(KeyId(CryptoConstants.KEY_ID), keyId)
    }

  @Test
  fun removeKey() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(secretKey).unwrap()

      // Remove key
      keyManager.removeKey(tryGetId(secretKey)!!).unwrap()

      // Check that no keys remain
      val keys = keyManager.getAllKeys().unwrap()
      assertEquals(0, keys.size)
    }

  @Test
  fun getKeyById() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(secretKey).unwrap()

      val keyId = keyManager.getKeyId(secretKey)
      assertNotNull(keyId)
      assertEquals(KeyId(CryptoConstants.KEY_ID), keyManager.getKeyId(secretKey))

      // Check returned key id matches the expected id and the created key id
      val returnedKey = keyManager.getKeyById(keyId).unwrap()
      assertEquals(keyManager.getKeyId(secretKey), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun getKeyByFullUserId() =
    scope.runTest {
      keyManager.addKey(secretKey).unwrap()

      val keyId = "${CryptoConstants.KEY_NAME} <${CryptoConstants.KEY_EMAIL}>"
      val returnedKey = keyManager.getKeyById(UserId(keyId)).unwrap()
      assertEquals(keyManager.getKeyId(secretKey), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun getKeyByEmailUserId() =
    scope.runTest {
      keyManager.addKey(secretKey).unwrap()

      val keyId = CryptoConstants.KEY_EMAIL
      val returnedKey = keyManager.getKeyById(UserId(keyId)).unwrap()
      assertEquals(keyManager.getKeyId(secretKey), keyManager.getKeyId(returnedKey))
    }

  @Test
  fun getNonExistentKey() =
    scope.runTest {
      // Add key using KeyManager
      keyManager.addKey(secretKey).unwrap()

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
      keyManager.addKey(secretKey).unwrap()
      keyManager.addKey(PGPKey(TestUtils.getArmoredSecretKeyWithMultipleIdentities())).unwrap()

      // Check if KeyManager returns one key
      val singleKeyList = keyManager.getAllKeys().unwrap()
      assertEquals(2, singleKeyList.size)
    }

  @Test
  fun getMultipleIdentityKeyWithAllIdentities() {
    scope.runTest {
      val key = PGPKey(TestUtils.getArmoredSecretKeyWithMultipleIdentities())
      keyManager.addKey(key).unwrap()

      val johnKey = keyManager.getKeyById(UserId("john@doe.org")).unwrap()
      val janeKey = keyManager.getKeyById(UserId("jane@doe.org")).unwrap()

      assertContentEquals(johnKey.contents, janeKey.contents)
    }
  }

  @Test
  fun replaceSecretKeyWithPublicKey() {
    scope.runTest {
      assertIs<Ok<PGPKey>>(keyManager.addKey(secretKey))
      assertIs<Err<KeyAlreadyExistsException>>(keyManager.addKey(publicKey))
    }
  }

  @Test
  fun replacePublicKeyWithSecretKey() {
    scope.runTest {
      assertIs<Ok<PGPKey>>(keyManager.addKey(publicKey))
      assertIs<Ok<PGPKey>>(keyManager.addKey(secretKey))
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
      assertIs<Ok<PGPKey>>(keyManager.addKey(secretKey))
      assertIs<Err<KeyAlreadyExistsException>>(keyManager.addKey(secretKey))
    }
  }

  @Test
  fun addMultipleKeysWithSameEmail() {
    scope.runTest {
      val alice =
        PGPKey(this::class.java.classLoader.getResource("alice_owner@example_com")!!.readBytes())
      val bobby =
        PGPKey(this::class.java.classLoader.getResource("bobby_owner@example_com")!!.readBytes())
      assertIs<Ok<PGPKey>>(keyManager.addKey(alice))
      assertIs<Ok<PGPKey>>(keyManager.addKey(bobby))

      keyManager.getAllKeys().apply {
        assertIs<Ok<List<PGPKey>>>(this)
        assertEquals(2, this.value.size)
      }

      val longKeyIds =
        arrayOf(
          KeyId(-7087927403306410599), // Alice
          KeyId(-961222705095032109), // Bobby
        )
      val userIds =
        arrayOf(
          UserId("Alice <owner@example.com>"),
          UserId("Bobby <owner@example.com>"),
        )

      for (idCollection in arrayOf(longKeyIds, userIds)) {
        val alice1 = keyManager.getKeyById(idCollection[0])
        val bobby1 = keyManager.getKeyById(idCollection[1])
        assertIs<Ok<PGPKey>>(alice1)
        assertIs<Ok<PGPKey>>(bobby1)
        assertNotEquals(alice1.value.contents, bobby1.value.contents)
      }
    }
  }
}
