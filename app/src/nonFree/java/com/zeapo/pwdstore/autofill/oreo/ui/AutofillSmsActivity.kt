/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo.ui

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
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.w
import com.google.android.gms.auth.api.phone.SmsCodeRetriever
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.tasks.Tasks
import com.zeapo.pwdstore.autofill.oreo.AutofillAction
import com.zeapo.pwdstore.autofill.oreo.Credentials
import com.zeapo.pwdstore.autofill.oreo.FillableForm
import com.zeapo.pwdstore.databinding.ActivityOreoAutofillSmsBinding
import com.zeapo.pwdstore.utils.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class AutofillSmsActivity : AppCompatActivity(), CoroutineScope {

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

    override val coroutineContext
        get() = Dispatchers.IO + SupervisorJob()

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
        launch {
            waitForSms()
        }
    }

    // Retry starting the SMS code retriever after a permission request.
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK)
            return
        launch {
            waitForSms()
        }
    }

    private fun waitForSms() {
        val smsClient = SmsCodeRetriever.getAutofillClient(this@AutofillSmsActivity)
        try {
            Tasks.await(smsClient.startSmsCodeRetriever())
        } catch (e: ResolvableApiException) {
            e.startResolutionForResult(this, 1)
        } catch (e: Exception) {
            e(e)
            finish()
        }
    }

    private val smsCodeRetrievedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val smsCode = intent.getStringExtra(SmsCodeRetriever.EXTRA_SMS_CODE)
            val fillInDataset =
                FillableForm.makeFillInDataset(
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

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancelChildren()
    }

}
