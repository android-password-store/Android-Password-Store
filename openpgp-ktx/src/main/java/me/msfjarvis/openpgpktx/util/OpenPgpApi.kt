/*
 * Copyright © 2014-2021 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
@file:Suppress("Unused")

package me.msfjarvis.openpgpktx.util

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openintents.openpgp.IOpenPgpService2
import org.openintents.openpgp.OpenPgpError

public class OpenPgpApi(private val context: Context, private val service: IOpenPgpService2) {

  private val pipeIdGen: AtomicInteger = AtomicInteger()

  public suspend fun executeApiAsync(
    data: Intent?,
    inputStream: InputStream?,
    outputStream: OutputStream?,
    callback: (intent: Intent?) -> Unit
  ) {
    val result = executeApi(data, inputStream, outputStream)
    withContext(Dispatchers.Main) { callback.invoke(result) }
  }

  public fun executeApi(data: Intent?, inputStream: InputStream?, outputStream: OutputStream?): Intent? {
    var input: ParcelFileDescriptor? = null
    return try {
      if (inputStream != null) {
        input = ParcelFileDescriptorUtil.pipeFrom(inputStream)
      }
      executeApi(data, input, outputStream)
    } catch (e: Exception) {
      Log.e(TAG, "Exception in executeApi call", e)
      val result = Intent()
      result.putExtra(RESULT_CODE, RESULT_CODE_ERROR)
      result.putExtra(RESULT_ERROR, OpenPgpError(OpenPgpError.CLIENT_SIDE_ERROR, e.message))
      result
    } finally {
      if (input != null) {
        try {
          input.close()
        } catch (e: IOException) {
          Log.e(TAG, "IOException when closing ParcelFileDescriptor!", e)
        }
      }
    }
  }

  /** InputStream and OutputStreams are always closed after operating on them! */
  private fun executeApi(data: Intent?, input: ParcelFileDescriptor?, os: OutputStream?): Intent? {
    var output: ParcelFileDescriptor? = null
    return try {
      // always send version from client
      data?.putExtra(EXTRA_API_VERSION, API_VERSION)
      val result: Intent
      var pumpThread: Thread? = null
      var outputPipeId = 0
      if (os != null) {
        outputPipeId = pipeIdGen.incrementAndGet()
        output = service.createOutputPipe(outputPipeId)
        pumpThread = ParcelFileDescriptorUtil.pipeTo(os, output)
      }
      // blocks until result is ready
      result = service.execute(data, input, outputPipeId)
      // set class loader to current context to allow unparcelling
      // of OpenPgpError and OpenPgpSignatureResult
      // http://stackoverflow.com/a/3806769
      result.setExtrasClassLoader(context.classLoader)
      // wait for ALL data being pumped from remote side
      pumpThread?.join()
      result
    } catch (e: Exception) {
      Log.e(TAG, "Exception in executeApi call", e)
      val result = Intent()
      result.putExtra(RESULT_CODE, RESULT_CODE_ERROR)
      result.putExtra(RESULT_ERROR, OpenPgpError(OpenPgpError.CLIENT_SIDE_ERROR, e.message))
      result
    } finally {
      // close() is required to halt the TransferThread
      if (output != null) {
        try {
          output.close()
        } catch (e: IOException) {
          Log.e(TAG, "IOException when closing ParcelFileDescriptor!", e)
        }
      }
    }
  }

  public companion object {

    private const val TAG = "OpenPgp API"

    public const val SERVICE_INTENT_2: String = "org.openintents.openpgp.IOpenPgpService2"

    /** see CHANGELOG.md */
    public const val API_VERSION: Int = 11

    /**
     * General extras --------------
     *
     * required extras: int EXTRA_API_VERSION (always required)
     *
     * returned extras: int RESULT_CODE (RESULT_CODE_ERROR, RESULT_CODE_SUCCESS or
     * RESULT_CODE_USER_INTERACTION_REQUIRED) OpenPgpError RESULT_ERROR (if RESULT_CODE ==
     * RESULT_CODE_ERROR) PendingIntent RESULT_INTENT (if RESULT_CODE ==
     * RESULT_CODE_USER_INTERACTION_REQUIRED)
     */

    /**
     * General extras --------------
     *
     * required extras: int EXTRA_API_VERSION (always required)
     *
     * returned extras: int RESULT_CODE (RESULT_CODE_ERROR, RESULT_CODE_SUCCESS or
     * RESULT_CODE_USER_INTERACTION_REQUIRED) OpenPgpError RESULT_ERROR (if RESULT_CODE ==
     * RESULT_CODE_ERROR) PendingIntent RESULT_INTENT (if RESULT_CODE ==
     * RESULT_CODE_USER_INTERACTION_REQUIRED)
     */
    /**
     * This action performs no operation, but can be used to check if the App has permission to
     * access the API in general, returning a user interaction PendingIntent otherwise. This can be
     * used to trigger the permission dialog explicitly.
     *
     * This action uses no extras.
     */
    public const val ACTION_CHECK_PERMISSION: String = "org.openintents.openpgp.action.CHECK_PERMISSION"

    /**
     * Sign text resulting in a cleartext signature Some magic pre-processing of the text is done to
     * convert it to a format usable for cleartext signatures per RFC 4880 before the text is
     * actually signed:
     * - end cleartext with newline
     * - remove whitespaces on line endings
     *
     * required extras: long EXTRA_SIGN_KEY_ID (key id of signing key)
     *
     * optional extras: char[] EXTRA_PASSPHRASE (key passphrase)
     */
    public const val ACTION_CLEARTEXT_SIGN: String = "org.openintents.openpgp.action.CLEARTEXT_SIGN"

    /**
     * Sign text or binary data resulting in a detached signature. No OutputStream necessary for
     * ACTION_DETACHED_SIGN (No magic pre-processing like in ACTION_CLEARTEXT_SIGN)! The detached
     * signature is returned separately in RESULT_DETACHED_SIGNATURE.
     *
     * required extras: long EXTRA_SIGN_KEY_ID (key id of signing key)
     *
     * optional extras: boolean EXTRA_REQUEST_ASCII_ARMOR (request ascii armor for detached
     * signature) char[] EXTRA_PASSPHRASE (key passphrase)
     *
     * returned extras: byte[] RESULT_DETACHED_SIGNATURE String RESULT_SIGNATURE_MICALG (contains
     * the name of the used signature algorithm as a string)
     */
    public const val ACTION_DETACHED_SIGN: String = "org.openintents.openpgp.action.DETACHED_SIGN"

    /**
     * Encrypt
     *
     * required extras: String[] EXTRA_USER_IDS (=emails of recipients, if more than one key has a
     * user_id, a PendingIntent is returned via RESULT_INTENT) or long[] EXTRA_KEY_IDS
     *
     * optional extras: boolean EXTRA_REQUEST_ASCII_ARMOR (request ascii armor for output) char[]
     * EXTRA_PASSPHRASE (key passphrase) String EXTRA_ORIGINAL_FILENAME (original filename to be
     * encrypted as metadata) boolean EXTRA_ENABLE_COMPRESSION (enable ZLIB compression, default ist
     * true)
     */
    public const val ACTION_ENCRYPT: String = "org.openintents.openpgp.action.ENCRYPT"

    /**
     * Sign and encrypt
     *
     * required extras: String[] EXTRA_USER_IDS (=emails of recipients, if more than one key has a
     * user_id, a PendingIntent is returned via RESULT_INTENT) or long[] EXTRA_KEY_IDS
     *
     * optional extras: long EXTRA_SIGN_KEY_ID (key id of signing key) boolean
     * EXTRA_REQUEST_ASCII_ARMOR (request ascii armor for output) char[] EXTRA_PASSPHRASE (key
     * passphrase) String EXTRA_ORIGINAL_FILENAME (original filename to be encrypted as metadata)
     * boolean EXTRA_ENABLE_COMPRESSION (enable ZLIB compression, default ist true)
     */
    public const val ACTION_SIGN_AND_ENCRYPT: String = "org.openintents.openpgp.action.SIGN_AND_ENCRYPT"

    public const val ACTION_QUERY_AUTOCRYPT_STATUS: String = "org.openintents.openpgp.action.QUERY_AUTOCRYPT_STATUS"

    /**
     * Decrypts and verifies given input stream. This methods handles encrypted-only,
     * signed-and-encrypted, and also signed-only input. OutputStream is optional, e.g., for
     * verifying detached signatures!
     *
     * If OpenPgpSignatureResult.getResult() == OpenPgpSignatureResult.RESULT_KEY_MISSING in
     * addition a PendingIntent is returned via RESULT_INTENT to download missing keys. On all other
     * status, in addition a PendingIntent is returned via RESULT_INTENT to open the key view in
     * OpenKeychain.
     *
     * optional extras: byte[] EXTRA_DETACHED_SIGNATURE (detached signature)
     *
     * returned extras: OpenPgpSignatureResult RESULT_SIGNATURE OpenPgpDecryptionResult
     * RESULT_DECRYPTION OpenPgpDecryptMetadata RESULT_METADATA String RESULT_CHARSET (charset which
     * was specified in the headers of ascii armored input, if any)
     */
    public const val ACTION_DECRYPT_VERIFY: String = "org.openintents.openpgp.action.DECRYPT_VERIFY"

    /**
     * Decrypts the header of an encrypted file to retrieve metadata such as original filename.
     *
     * This does not decrypt the actual content of the file.
     *
     * returned extras: OpenPgpDecryptMetadata RESULT_METADATA String RESULT_CHARSET (charset which
     * was specified in the headers of ascii armored input, if any)
     */
    public const val ACTION_DECRYPT_METADATA: String = "org.openintents.openpgp.action.DECRYPT_METADATA"

    /**
     * Select key id for signing
     *
     * optional extras: String EXTRA_USER_ID
     *
     * returned extras: long EXTRA_SIGN_KEY_ID
     */
    public const val ACTION_GET_SIGN_KEY_ID: String = "org.openintents.openpgp.action.GET_SIGN_KEY_ID"

    /**
     * Get key ids based on given user ids (=emails)
     *
     * required extras: String[] EXTRA_USER_IDS
     *
     * returned extras: long[] RESULT_KEY_IDS
     */
    public const val ACTION_GET_KEY_IDS: String = "org.openintents.openpgp.action.GET_KEY_IDS"

    /**
     * This action returns RESULT_CODE_SUCCESS if the OpenPGP Provider already has the key
     * corresponding to the given key id in its database.
     *
     * It returns RESULT_CODE_USER_INTERACTION_REQUIRED if the Provider does not have the key. The
     * PendingIntent from RESULT_INTENT can be used to retrieve those from a keyserver.
     *
     * If an Output stream has been defined the whole public key is returned. required extras: long
     * EXTRA_KEY_ID
     *
     * optional extras: String EXTRA_REQUEST_ASCII_ARMOR (request that the returned key is encoded
     * in ASCII Armor)
     */
    public const val ACTION_GET_KEY: String = "org.openintents.openpgp.action.GET_KEY"

    /**
     * Backup all keys given by EXTRA_KEY_IDS and if requested their secret parts. The encrypted
     * backup will be written to the OutputStream. The client app has no access to the backup code
     * used to encrypt the backup! This operation always requires user interaction with
     * RESULT_CODE_USER_INTERACTION_REQUIRED!
     *
     * required extras: long[] EXTRA_KEY_IDS (keys that should be included in the backup) boolean
     * EXTRA_BACKUP_SECRET (also backup secret keys)
     */
    public const val ACTION_BACKUP: String = "org.openintents.openpgp.action.BACKUP"

    public const val ACTION_UPDATE_AUTOCRYPT_PEER: String = "org.openintents.openpgp.action.UPDATE_AUTOCRYPT_PEER"

    /* Intent extras */
    public const val EXTRA_API_VERSION: String = "api_version"

    // ACTION_DETACHED_SIGN, ENCRYPT, SIGN_AND_ENCRYPT, DECRYPT_VERIFY
    // request ASCII Armor for output
    // OpenPGP Radix-64, 33 percent overhead compared to binary, see
    // http://tools.ietf.org/html/rfc4880#page-53)
    public const val EXTRA_REQUEST_ASCII_ARMOR: String = "ascii_armor"

    // ACTION_DETACHED_SIGN
    public const val RESULT_DETACHED_SIGNATURE: String = "detached_signature"
    public const val RESULT_SIGNATURE_MICALG: String = "signature_micalg"

    // ENCRYPT, SIGN_AND_ENCRYPT, QUERY_AUTOCRYPT_STATUS
    public const val EXTRA_USER_IDS: String = "user_ids"
    public const val EXTRA_KEY_IDS: String = "key_ids"
    public const val EXTRA_KEY_IDS_SELECTED: String = "key_ids_selected"
    public const val EXTRA_SIGN_KEY_ID: String = "sign_key_id"

    public const val RESULT_KEYS_CONFIRMED: String = "keys_confirmed"
    public const val RESULT_AUTOCRYPT_STATUS: String = "autocrypt_status"
    public const val AUTOCRYPT_STATUS_UNAVAILABLE: Int = 0
    public const val AUTOCRYPT_STATUS_DISCOURAGE: Int = 1
    public const val AUTOCRYPT_STATUS_AVAILABLE: Int = 2
    public const val AUTOCRYPT_STATUS_MUTUAL: Int = 3

    // optional extras:
    public const val EXTRA_PASSPHRASE: String = "passphrase"
    public const val EXTRA_ORIGINAL_FILENAME: String = "original_filename"
    public const val EXTRA_ENABLE_COMPRESSION: String = "enable_compression"
    public const val EXTRA_OPPORTUNISTIC_ENCRYPTION: String = "opportunistic"

    // GET_SIGN_KEY_ID
    public const val EXTRA_USER_ID: String = "user_id"

    // GET_KEY
    public const val EXTRA_KEY_ID: String = "key_id"
    public const val EXTRA_MINIMIZE: String = "minimize"
    public const val EXTRA_MINIMIZE_USER_ID: String = "minimize_user_id"
    public const val RESULT_KEY_IDS: String = "key_ids"

    // BACKUP
    public const val EXTRA_BACKUP_SECRET: String = "backup_secret"

    /* Service Intent returns */
    public const val RESULT_CODE: String = "result_code"

    // get actual error object from RESULT_ERROR
    public const val RESULT_CODE_ERROR: Int = 0

    // success!
    public const val RESULT_CODE_SUCCESS: Int = 1

    // get PendingIntent from RESULT_INTENT, start PendingIntent with
    // startIntentSenderForResult,
    // and execute service method again in onActivityResult
    public const val RESULT_CODE_USER_INTERACTION_REQUIRED: Int = 2

    public const val RESULT_ERROR: String = "error"
    public const val RESULT_INTENT: String = "intent"

    // DECRYPT_VERIFY
    public const val EXTRA_DETACHED_SIGNATURE: String = "detached_signature"
    public const val EXTRA_PROGRESS_MESSENGER: String = "progress_messenger"
    public const val EXTRA_DATA_LENGTH: String = "data_length"
    public const val EXTRA_DECRYPTION_RESULT: String = "decryption_result"
    public const val EXTRA_SENDER_ADDRESS: String = "sender_address"
    public const val EXTRA_SUPPORT_OVERRIDE_CRYPTO_WARNING: String = "support_override_crpto_warning"
    public const val EXTRA_AUTOCRYPT_PEER_ID: String = "autocrypt_peer_id"
    public const val EXTRA_AUTOCRYPT_PEER_UPDATE: String = "autocrypt_peer_update"
    public const val EXTRA_AUTOCRYPT_PEER_GOSSIP_UPDATES: String = "autocrypt_peer_gossip_updates"
    public const val RESULT_SIGNATURE: String = "signature"
    public const val RESULT_DECRYPTION: String = "decryption"
    public const val RESULT_METADATA: String = "metadata"
    public const val RESULT_INSECURE_DETAIL_INTENT: String = "insecure_detail_intent"
    public const val RESULT_OVERRIDE_CRYPTO_WARNING: String = "override_crypto_warning"

    // This will be the charset which was specified in the headers of ascii armored input, if
    // any
    public const val RESULT_CHARSET: String = "charset"

    // INTERNAL, must not be used
    internal const val EXTRA_CALL_UUID1 = "call_uuid1"
    internal const val EXTRA_CALL_UUID2 = "call_uuid2"
  }
}
