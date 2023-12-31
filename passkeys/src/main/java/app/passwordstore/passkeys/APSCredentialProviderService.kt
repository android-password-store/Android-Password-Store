package app.passwordstore.passkeys

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest

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
    callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
  ) {}

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
    println(request.requestJson)
    createEntries.add(
      CreateEntry(
        DEFAULT_ACCOUNT_NAME,
        createNewPendingIntent(DEFAULT_ACCOUNT_NAME, CREATE_PASSKEY_INTENT_ACTION)
      )
    )

    return BeginCreateCredentialResponse(createEntries)
  }

  private fun createNewPendingIntent(accountId: String, action: String): PendingIntent {
    val intent = Intent(action).setPackage(packageName)
    // Add your local account ID as an extra to the intent, so that when
    // user selects this entry, the credential can be saved to this
    // account
    intent.putExtra(EXTRA_KEY_ACCOUNT_ID, accountId)

    return PendingIntent.getActivity(
      applicationContext,
      REQUEST_CODE,
      intent,
      (PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
    )
  }

  internal companion object {

    // These intent actions are specified for corresponding activities
    // that are to be invoked through the PendingIntent(s)
    const val REQUEST_CODE = 1010101
    const val EXTRA_KEY_ACCOUNT_ID = "EXTRA_KEY_ACCOUNT_ID"
    const val DEFAULT_ACCOUNT_NAME = "Default Password Store"
    const val CREATE_PASSKEY_INTENT_ACTION = "app.passwordstore.CREATE_PASSKEY"
    const val GET_PASSKEY_INTENT_ACTION = "PACKAGE_NAME.GET_PASSKEY"
  }
}
