/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.git.sshj

import android.app.PendingIntent
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.github.ajalt.timberkt.d
import com.zeapo.pwdstore.utils.OPENPGP_PROVIDER
import com.zeapo.pwdstore.utils.PreferenceKeys
import java.io.Closeable
import java.security.PublicKey
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.common.Base64
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.openintents.ssh.authentication.ISshAuthenticationService
import org.openintents.ssh.authentication.SshAuthenticationApi
import org.openintents.ssh.authentication.SshAuthenticationApiError
import org.openintents.ssh.authentication.SshAuthenticationConnection
import org.openintents.ssh.authentication.request.KeySelectionRequest
import org.openintents.ssh.authentication.request.Request
import org.openintents.ssh.authentication.request.SigningRequest
import org.openintents.ssh.authentication.request.SshPublicKeyRequest
import org.openintents.ssh.authentication.response.KeySelectionResponse
import org.openintents.ssh.authentication.response.Response
import org.openintents.ssh.authentication.response.SigningResponse
import org.openintents.ssh.authentication.response.SshPublicKeyResponse

class OpenKeychainKeyProvider private constructor(private val activity: FragmentActivity) : KeyProvider, Closeable {

    companion object {
        suspend fun prepareAndUse(activity: FragmentActivity, block: (provider: OpenKeychainKeyProvider) -> Unit) {
            OpenKeychainKeyProvider(activity).prepareAndUse(block)
        }
    }

    private sealed class ApiResponse {
        data class Success(val response: Response) : ApiResponse()
        data class GeneralError(val exception: Exception) : ApiResponse()
        data class NoSuchKey(val exception: Exception) : ApiResponse()
    }

    private val context = activity.applicationContext
    private val sshServiceConnection = SshAuthenticationConnection(context, OPENPGP_PROVIDER)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    private lateinit var continueAfterUserInteraction: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var sshServiceApi: SshAuthenticationApi

    private var currentCont: Continuation<Intent>? = null
    private var keyId
        get() = preferences.getString(PreferenceKeys.SSH_OPENKEYSTORE_KEYID, null)
        set(value) {
            preferences.edit {
                putString(PreferenceKeys.SSH_OPENKEYSTORE_KEYID, value)
            }
        }
    private var publicKey: PublicKey? = null
    private var privateKey: OpenKeychainPrivateKey? = null

    private suspend fun prepareAndUse(block: (provider: OpenKeychainKeyProvider) -> Unit) {
        prepare()
        use(block)
    }

    private suspend fun prepare() {
        continueAfterUserInteraction = withContext(Dispatchers.Main) {
            activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                currentCont?.let { cont ->
                    currentCont = null
                    val data = result.data
                    if (data != null)
                        cont.resume(data)
                    else
                        cont.resumeWithException(UserAuthException(DisconnectReason.AUTH_CANCELLED_BY_USER))
                }
            }
        }

        sshServiceApi = suspendCoroutine { cont ->
            sshServiceConnection.connect(object : SshAuthenticationConnection.OnBound {
                override fun onBound(sshAgent: ISshAuthenticationService) {
                    d { "Bound to SshAuthenticationApi: $OPENPGP_PROVIDER" }
                    cont.resume(SshAuthenticationApi(context, sshAgent))
                }

                override fun onError() {
                    throw UserAuthException(DisconnectReason.UNKNOWN, "OpenKeychain service unavailable")
                }
            })
        }

        if (keyId == null) {
            selectKey()
        }
        check(keyId != null)
        fetchPublicKey()
        makePrivateKey()
    }

    private suspend fun fetchPublicKey(isRetry: Boolean = false) {
        when (val sshPublicKeyResponse = executeApiRequest(SshPublicKeyRequest(keyId))) {
            is ApiResponse.Success -> {
                val response = sshPublicKeyResponse.response as SshPublicKeyResponse
                val sshPublicKey = response.sshPublicKey!!
                val sshKeyParts = sshPublicKey.split("""\s+""".toRegex())
                check(sshKeyParts.size >= 2) { "OpenKeychain API returned invalid SSH key" }
                @Suppress("BlockingMethodInNonBlockingContext")
                publicKey = Buffer.PlainBuffer(Base64.decode(sshKeyParts[1])).readPublicKey()
            }
            is ApiResponse.NoSuchKey -> if (isRetry) {
                throw sshPublicKeyResponse.exception
            } else {
                // Allow the user to reselect an authentication key and retry
                selectKey()
                fetchPublicKey(true)
            }
            is ApiResponse.GeneralError -> throw sshPublicKeyResponse.exception
        }
    }

    private suspend fun selectKey() {
        when (val keySelectionResponse = executeApiRequest(KeySelectionRequest())) {
            is ApiResponse.Success -> keyId = (keySelectionResponse.response as KeySelectionResponse).keyId
            is ApiResponse.GeneralError -> throw keySelectionResponse.exception
            is ApiResponse.NoSuchKey -> throw keySelectionResponse.exception
        }
    }

    private suspend fun executeApiRequest(request: Request, resultOfUserInteraction: Intent? = null): ApiResponse {
        d { "executeRequest($request) called" }
        val result = withContext(Dispatchers.Main) {
            // If the request required user interaction, the data returned from the PendingIntent
            // is used as the real request.
            sshServiceApi.executeApi(resultOfUserInteraction ?: request.toIntent())!!
        }
        return parseResult(request, result).also {
            d { "executeRequest($request): $it" }
        }
    }

    private suspend fun parseResult(request: Request, result: Intent): ApiResponse {
        return when (result.getIntExtra(SshAuthenticationApi.EXTRA_RESULT_CODE, SshAuthenticationApi.RESULT_CODE_ERROR)) {
            SshAuthenticationApi.RESULT_CODE_SUCCESS -> {
                ApiResponse.Success(when (request) {
                    is KeySelectionRequest -> KeySelectionResponse(result)
                    is SshPublicKeyRequest -> SshPublicKeyResponse(result)
                    is SigningRequest -> SigningResponse(result)
                    else -> throw IllegalArgumentException("Unsupported OpenKeychain request type")
                })
            }
            SshAuthenticationApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                val pendingIntent: PendingIntent = result.getParcelableExtra(SshAuthenticationApi.EXTRA_PENDING_INTENT)!!
                val resultOfUserInteraction: Intent = withContext(Dispatchers.Main) {
                    suspendCoroutine { cont ->
                        currentCont = cont
                        continueAfterUserInteraction.launch(IntentSenderRequest.Builder(pendingIntent).build())
                    }
                }
                executeApiRequest(request, resultOfUserInteraction)
            }
            else -> {
                val error = result.getParcelableExtra<SshAuthenticationApiError>(SshAuthenticationApi.EXTRA_ERROR)
                val exception = UserAuthException(DisconnectReason.UNKNOWN, "Request ${request::class.simpleName} failed: ${error?.message}")
                when (error?.error) {
                    SshAuthenticationApiError.NO_AUTH_KEY, SshAuthenticationApiError.NO_SUCH_KEY -> ApiResponse.NoSuchKey(exception)
                    else -> ApiResponse.GeneralError(exception)
                }
            }
        }
    }

    private fun makePrivateKey() {
        check(keyId != null && publicKey != null)
        privateKey = object : OpenKeychainPrivateKey {
            override suspend fun sign(challenge: ByteArray, hashAlgorithm: Int) =
                when (val signingResponse = executeApiRequest(SigningRequest(challenge, keyId, hashAlgorithm))) {
                    is ApiResponse.Success -> (signingResponse.response as SigningResponse).signature
                    is ApiResponse.GeneralError -> throw signingResponse.exception
                    is ApiResponse.NoSuchKey -> throw signingResponse.exception
                }

            override fun getAlgorithm() = publicKey!!.algorithm
        }
    }

    override fun close() {
        continueAfterUserInteraction.unregister()
        sshServiceConnection.disconnect()
    }

    override fun getPrivate() = privateKey

    override fun getPublic() = publicKey

    override fun getType() = KeyType.fromKey(publicKey)
}
