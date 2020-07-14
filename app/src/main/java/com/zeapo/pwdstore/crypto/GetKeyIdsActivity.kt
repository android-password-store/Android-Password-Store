/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.crypto

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.Timber
import com.github.ajalt.timberkt.e
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import org.openintents.openpgp.IOpenPgpService2

class GetKeyIdsActivity : BasePgpActivity() {

    private val getKeyIds = registerForActivityResult(StartActivityForResult()) { getKeyIds() }

    private val userInteractionRequiredResult = registerForActivityResult(StartIntentSenderForResult()) { result ->
        if (result.data == null) {
            setResult(RESULT_CANCELED, null)
            finish()
            return@registerForActivityResult
        }

        when (result.resultCode) {
            RESULT_OK -> getKeyIds(result.data)
            RESULT_CANCELED -> {
                setResult(RESULT_CANCELED, result.data)
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindToOpenKeychain(this, getKeyIds)
    }

    override fun onBound(service: IOpenPgpService2) {
        super.onBound(service)
        getKeyIds()
    }

    override fun onError(e: Exception) {
        e(e)
    }

    /**
     * Get the Key ids from OpenKeychain
     */
    private fun getKeyIds(receivedIntent: Intent? = null) {
        val data = receivedIntent ?: Intent()
        data.action = OpenPgpApi.ACTION_GET_KEY_IDS
        lifecycleScope.launch(Dispatchers.IO) {
            api?.executeApiAsync(data, null, null) { result ->
                when (result?.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                    OpenPgpApi.RESULT_CODE_SUCCESS -> {
                        try {
                            val ids = result.getLongArrayExtra(OpenPgpApi.RESULT_KEY_IDS)
                                ?: LongArray(0)
                            val keys = ids.map { it.toString() }.toSet()
                            // use Long
                            settings.edit { putStringSet(PreferenceKeys.OPENPGP_KEY_IDS_SET, keys) }
                            snackbar(message = "PGP keys selected")
                            setResult(RESULT_OK)
                            finish()
                        } catch (e: Exception) {
                            Timber.e(e) { "An Exception occurred" }
                        }
                    }
                    OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED -> {
                        val sender = getUserInteractionRequestIntent(result)
                        userInteractionRequiredResult.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    OpenPgpApi.RESULT_CODE_ERROR -> handleError(result)
                }
            }
        }
    }
}
