package app.passwordstore.passkeys

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import androidx.credentials.webauthn.PublicKeyCredentialRequestOptions
import java.io.File
import logcat.logcat

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class APSCredentialProviderService : CredentialProviderService() {

  override fun onBeginCreateCredentialRequest(
    request: BeginCreateCredentialRequest,
    cancellationSignal: CancellationSignal,
    callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
  ) {
    val response: BeginCreateCredentialResponse? = processCreateCredentialRequest(request)
    if (response != null) {
      callback.onResult(response)
    } else {
      callback.onError(CreateCredentialUnknownException())
    }
  }

  override fun onBeginGetCredentialRequest(
    request: BeginGetCredentialRequest,
    cancellationSignal: CancellationSignal,
    callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
  ) {
    try {
      val response = processGetCredentialsRequest(request)
      callback.onResult(response)
    } catch (e: GetCredentialException) {
      callback.onError(GetCredentialUnknownException())
    }

    return
  }

  override fun onClearCredentialStateRequest(
    request: ProviderClearCredentialStateRequest,
    cancellationSignal: CancellationSignal,
    callback: OutcomeReceiver<Void?, ClearCredentialException>,
  ) {}

  private fun processCreateCredentialRequest(
    request: BeginCreateCredentialRequest
  ): BeginCreateCredentialResponse? {
    return when (request) {
      is BeginCreatePublicKeyCredentialRequest -> {
        // Request is passkey type
        handleCreatePasskeyQuery(request)
      }
      // Request not supported
      else -> null
    }
  }

  private fun handleCreatePasskeyQuery(
    @Suppress("UNUSED_PARAMETER") request: BeginCreatePublicKeyCredentialRequest
  ): BeginCreateCredentialResponse {
    val createEntries: MutableList<CreateEntry> = mutableListOf()
    createEntries.add(
      CreateEntry(
        DEFAULT_ACCOUNT_NAME,
        createNewPendingIntent(CREATE_PASSKEY_INTENT_ACTION, CREATE_REQUEST_CODE),
      )
    )

    return BeginCreateCredentialResponse(createEntries)
  }

  private fun processGetCredentialsRequest(
    request: BeginGetCredentialRequest
  ): BeginGetCredentialResponse {
    val callingPackage = request.callingAppInfo?.packageName
    val credentialEntries: MutableList<CredentialEntry> = mutableListOf()

    for (option in request.beginGetCredentialOptions) {
      when (option) {
        is BeginGetPublicKeyCredentialOption -> {
          credentialEntries.addAll(populatePasskeyData(callingPackage, option))
        }
        else -> {
          logcat { "Request not supported" }
        }
      }
    }

    return BeginGetCredentialResponse(credentialEntries)
  }

  @SuppressLint("RestrictedApi")
  private fun populatePasskeyData(
    callingPackage: String?,
    option: BeginGetPublicKeyCredentialOption,
  ): List<CredentialEntry> {
    if (callingPackage.isNullOrEmpty()) return emptyList()

    // Get your credentials from database where you saved during creation flow
    val passkeysDir = File(filesDir.toString(), "/store/passkeys")
    val appDir = File(passkeysDir, callingPackage)
    if (!appDir.exists()) return emptyList()

    // Get all passkeys for this package
    val passkeyEntries: MutableList<CredentialEntry> = mutableListOf()
    @Suppress("UNUSED_VARIABLE") val request = PublicKeyCredentialRequestOptions(option.requestJson)
    val usernames =
      appDir.listFiles()?.filter(File::isDirectory)?.map(File::getName) ?: return emptyList()

    for (username in usernames) {
      val data = Bundle()
      passkeyEntries.add(
        PublicKeyCredentialEntry(
          context = applicationContext,
          username = username,
          pendingIntent = createNewPendingIntent(GET_PASSKEY_INTENT_ACTION, GET_REQUEST_CODE, data),
          beginGetPublicKeyCredentialOption = option,
        )
      )
    }
    return passkeyEntries
  }

  private fun createNewPendingIntent(
    action: String,
    requestCode: Int,
    extra: Bundle? = null,
  ): PendingIntent {
    val intent = Intent(action).setPackage(packageName)
    extra?.let { intent.putExtra("CREDENTIAL_DATA", extra) }

    return PendingIntent.getActivity(
      applicationContext,
      requestCode,
      intent,
      (PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
    )
  }

  internal companion object {

    // These intent actions are specified for corresponding activities
    // that are to be invoked through the PendingIntent(s)
    const val CREATE_REQUEST_CODE = 10001
    const val GET_REQUEST_CODE = 10002
    const val DEFAULT_ACCOUNT_NAME = "Default Password Store"
    const val CREATE_PASSKEY_INTENT_ACTION = "app.passwordstore.CREATE_PASSKEY"
    const val GET_PASSKEY_INTENT_ACTION = "app.passwordstore.GET_PASSKEY"
  }
}
