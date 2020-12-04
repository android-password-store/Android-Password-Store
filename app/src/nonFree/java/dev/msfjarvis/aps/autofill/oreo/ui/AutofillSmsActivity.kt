/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package dev.msfjarvis.aps.autofill.oreo.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.w
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.Credentials
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.gms.auth.api.phone.SmsCodeRetriever
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.tasks.Task
import dev.msfjarvis.aps.util.autofill.AutofillResponseBuilder
import dev.msfjarvis.aps.databinding.ActivityOreoAutofillSmsBinding
import dev.msfjarvis.aps.util.extensions.viewBinding
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun <T> Task<T>.suspendableAwait() = suspendCoroutine<T> { cont ->
    addOnSuccessListener { result: T ->
        cont.resume(result)
    }
    addOnFailureListener { e ->
        // Unwrap specific exceptions (e.g. ResolvableApiException) from ExecutionException.
        val cause = (e as? ExecutionException)?.cause ?: e
        cont.resumeWithException(cause)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class AutofillSmsActivity : AppCompatActivity() {

    companion object {

        private var fillOtpFromSmsRequestCode = 1

        fun shouldOfferFillFromSms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                return false
            val googleApiAvailabilityInstance = GoogleApiAvailability.getInstance()
            val googleApiStatus = googleApiAvailabilityInstance.isGooglePlayServicesAvailable(context)
            if (googleApiStatus != ConnectionResult.SUCCESS) {
                w { "Google Play Services unavailable or not updated: ${googleApiAvailabilityInstance.getErrorString(googleApiStatus)}" }
                return false
            }
            // https://developer.android.com/guide/topics/text/autofill-services#sms-autofill
            if (googleApiAvailabilityInstance.getApkVersion(context) < 190056000) {
                w { "Google Play Service 19.0.56 or higher required for SMS OTP Autofill" }
                return false
            }
            return true
        }

        fun makeFillOtpFromSmsIntentSender(context: Context): IntentSender {
            val intent = Intent(context, AutofillSmsActivity::class.java)
            return PendingIntent.getActivity(
                context,
                fillOtpFromSmsRequestCode++,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT
            ).intentSender
        }
    }

    private val binding by viewBinding(ActivityOreoAutofillSmsBinding::inflate)

    private lateinit var clientState: Bundle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setResult(RESULT_CANCELED)
        binding.cancelButton.setOnClickListener {
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        clientState = intent?.getBundleExtra(AutofillManager.EXTRA_CLIENT_STATE) ?: run {
            e { "AutofillSmsActivity started without EXTRA_CLIENT_STATE" }
            finish()
            return
        }

        registerReceiver(smsCodeRetrievedReceiver, IntentFilter(SmsCodeRetriever.SMS_CODE_RETRIEVED_ACTION), SmsRetriever.SEND_PERMISSION, null)
        lifecycleScope.launch {
            waitForSms()
        }
    }

    // Retry starting the SMS code retriever after a permission request.
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK)
            return
        lifecycleScope.launch {
            waitForSms()
        }
    }

    private suspend fun waitForSms() {
        val smsClient = SmsCodeRetriever.getAutofillClient(this@AutofillSmsActivity)
        runCatching {
            withContext(Dispatchers.IO) {
                smsClient.startSmsCodeRetriever().suspendableAwait()
            }
        }.onFailure { e ->
            if (e is ResolvableApiException) {
                e.startResolutionForResult(this@AutofillSmsActivity, 1)
            } else {
                e(e)
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    private val smsCodeRetrievedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val smsCode = intent.getStringExtra(SmsCodeRetriever.EXTRA_SMS_CODE)
            val fillInDataset = AutofillResponseBuilder.makeFillInDataset(
                this@AutofillSmsActivity,
                Credentials(null, null, smsCode),
                clientState,
                AutofillAction.FillOtpFromSms
            )
            setResult(RESULT_OK, Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, fillInDataset)
            })
            finish()
        }
    }
}
