/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.zeapo.pwdstore.crypto

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.e
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.msfjarvis.openpgpktx.util.OpenPgpApi
import me.msfjarvis.openpgpktx.util.OpenPgpUtils
import org.openintents.openpgp.IOpenPgpService2

class GetKeyIdsActivity : BasePgpActivity() {

    private val userInteractionRequiredResult = registerForActivityResult(StartIntentSenderForResult()) { result ->
        if (result.data == null || result.resultCode == RESULT_CANCELED) {
            setResult(RESULT_CANCELED, result.data)
            finish()
            return@registerForActivityResult
        }
        getKeyIds(result.data!!)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindToOpenKeychain(this)
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
    private fun getKeyIds(data: Intent = Intent()) {
        data.action = OpenPgpApi.ACTION_GET_KEY_IDS
        lifecycleScope.launch(Dispatchers.IO) {
            api?.executeApiAsync(data, null, null) { result ->
                when (result?.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
                    OpenPgpApi.RESULT_CODE_SUCCESS -> {
                        runCatching {
                            val ids = result.getLongArrayExtra(OpenPgpApi.RESULT_KEY_IDS)?.map {
                                OpenPgpUtils.convertKeyIdToHex(it)
                            } ?: emptyList()
                            val keyResult = Intent().putExtra(OpenPgpApi.EXTRA_KEY_IDS, ids.toTypedArray())
                            setResult(RESULT_OK, keyResult)
                            finish()
                        }.onFailure { e ->
                            e(e)
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
