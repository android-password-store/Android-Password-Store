/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.util.git.sshj

import android.app.PendingIntent
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.d
import dev.msfjarvis.aps.util.extensions.OPENPGP_PROVIDER
import dev.msfjarvis.aps.util.extensions.sharedPrefs
import dev.msfjarvis.aps.util.settings.PreferenceKeys
import java.io.Closeable
import java.security.PublicKey
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

class OpenKeychainKeyProvider private constructor(val activity: ContinuationContainerActivity) : KeyProvider, Closeable {

    companion object {

        suspend fun prepareAndUse(activity: ContinuationContainerActivity, block: (provider: OpenKeychainKeyProvider) -> Unit) {
            withContext(Dispatchers.Main) {
                OpenKeychainKeyProvider(activity)
            }.prepareAndUse(block)
        }
    }

    private sealed class ApiResponse {
        data class Success(val response: Response) : ApiResponse()
        data class GeneralError(val exception: Exception) : ApiResponse()
        data class NoSuchKey(val exception: Exception) : ApiResponse()
    }

    private val context = activity.applicationContext
    private val sshServiceConnection = SshAuthenticationConnection(context, OPENPGP_PROVIDER)
    private val preferences = context.sharedPrefs
    private lateinit var sshServiceApi: SshAuthenticationApi

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
                publicKey = parseSshPublicKey(sshPublicKey)
                    ?: throw IllegalStateException("OpenKeychain API returned invalid SSH key")
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
                        activity.stashedCont = cont
                        activity.continueAfterUserInteraction.launch(IntentSenderRequest.Builder(pendingIntent).build())
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
        activity.lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                activity.continueAfterUserInteraction.unregister()
            }
        }
        sshServiceConnection.disconnect()
    }

    override fun getPrivate() = privateKey

    override fun getPublic() = publicKey

    override fun getType(): KeyType = KeyType.fromKey(publicKey)
}
