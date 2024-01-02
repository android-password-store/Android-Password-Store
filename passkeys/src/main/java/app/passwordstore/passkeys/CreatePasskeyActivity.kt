package app.passwordstore.passkeys

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.webauthn.AuthenticatorAttestationResponse
import androidx.credentials.webauthn.FidoPublicKeyCredential
import androidx.credentials.webauthn.PublicKeyCredentialCreationOptions
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@RequiresApi(34)
public class CreatePasskeyActivity : FragmentActivity() {

  @SuppressLint("RestrictedApi")
  private fun createAuthenticationCallback(
    request: PublicKeyCredentialCreationOptions,
    callingAppInfo: CallingAppInfo,
  ): BiometricPrompt.AuthenticationCallback {
    return object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        finish()
      }

      override fun onAuthenticationFailed() {
        super.onAuthenticationFailed()
        finish()
      }

      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)

        // Generate a credentialId
        val credentialId = ByteArray(32)
        SecureRandom().nextBytes(credentialId)

        // Generate a credential key pair
        val spec = ECGenParameterSpec("secp256r1")
        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(spec)
        val keyPair = keyPairGen.genKeyPair()

        // Save passkey in your database as per your own implementation
        val passkeysDir = File(filesDir.toString(), "/store/passkeys")
        if (!passkeysDir.exists()) passkeysDir.mkdirs()

        val folderName = callingAppInfo.packageName
        val subfolderName = request.user.name
        val keyDir = File(passkeysDir, "/$folderName/$subfolderName")
        if (!keyDir.exists()) keyDir.mkdirs()

        val publicKey = File(keyDir, "public.key")
        val privateKey = File(keyDir, "private.key")
        publicKey.writeBytes(keyPair.public.encoded)
        privateKey.writeBytes(keyPair.private.encoded)

        // Create AuthenticatorAttestationResponse object to pass to FidoPublicKeyCredential
        val response =
          AuthenticatorAttestationResponse(
            requestOptions = request,
            credentialId = credentialId,
            credentialPublicKey = keyPair.public.encoded,
            origin = appInfoToOrigin(callingAppInfo),
            up = true,
            uv = true,
            be = true,
            bs = true,
            packageName = callingAppInfo.packageName,
          )

        // https://w3c.github.io/webauthn/#enum-attachment
        val credential =
          FidoPublicKeyCredential(
            rawId = credentialId,
            response = response,
            authenticatorAttachment = "platform",
          )
        val intent = Intent()
        val createPublicKeyCredResponse = CreatePublicKeyCredentialResponse(credential.json())

        // Set the CreateCredentialResponse as the result of the Activity
        PendingIntentHandler.setCreateCredentialResponse(intent, createPublicKeyCredResponse)
        setResult(Activity.RESULT_OK, intent)
        finish()
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)

    if (request != null && request.callingRequest is CreatePublicKeyCredentialRequest) {
      val publicKeyRequest = request.callingRequest as CreatePublicKeyCredentialRequest
      println(publicKeyRequest.requestJson)

      createPasskey(
        publicKeyRequest.requestJson,
        request.callingAppInfo,
        publicKeyRequest.clientDataHash,
      )
    }
  }

  @SuppressLint("RestrictedApi")
  private fun createPasskey(
    requestJson: String,
    callingAppInfo: CallingAppInfo,
    @Suppress("UNUSED_PARAMETER") clientDataHash: ByteArray?,
  ) {
    val request = PublicKeyCredentialCreationOptions(requestJson)
    val biometricPrompt =
      BiometricPrompt(this, createAuthenticationCallback(request, callingAppInfo))
    val promptInfo =
      BiometricPrompt.PromptInfo.Builder()
        .setTitle("Use your screen lock")
        .setSubtitle("Create passkey for ${request.rp.name}")
        .setNegativeButtonText("Cancel")
        .setAllowedAuthenticators(
          BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) /* or BiometricManager.Authenticators.DEVICE_CREDENTIAL */
        .build()
    biometricPrompt.authenticate(promptInfo)
  }

  @OptIn(ExperimentalEncodingApi::class)
  private fun appInfoToOrigin(info: CallingAppInfo): String {
    val cert = info.signingInfo.apkContentsSigners[0].toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val certHash = md.digest(cert)
    // This is the format for origin
    return "android:apk-key-hash:${Base64.encode(certHash)}"
  }
}
