/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.autofill.oreo

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.github.ajalt.timberkt.e
import com.github.michaelbull.result.fold
import com.github.androidpasswordstore.autofillparser.AutofillAction
import com.github.androidpasswordstore.autofillparser.AutofillScenario
import com.github.androidpasswordstore.autofillparser.Credentials
import com.github.androidpasswordstore.autofillparser.FillableForm
import com.github.androidpasswordstore.autofillparser.fillWith
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillDecryptActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillFilterView
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillPublisherChangedActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillSaveActivity
import com.zeapo.pwdstore.autofill.oreo.ui.AutofillSmsActivity
import java.io.File

@RequiresApi(Build.VERSION_CODES.O)
class AutofillResponseBuilder(form: FillableForm) {
    private val formOrigin = form.formOrigin
    private val scenario = form.scenario
    private val ignoredIds = form.ignoredIds
    private val saveFlags = form.saveFlags
    private val clientState = form.toClientState()

    // We do not offer save when the only relevant field is a username field or there is no field.
    private val scenarioSupportsSave =
        scenario.fieldsToSave.minus(listOfNotNull(scenario.username)).isNotEmpty()
    private val canBeSaved = saveFlags != null && scenarioSupportsSave

    private fun makePlaceholderDataset(
        remoteView: RemoteViews,
        intentSender: IntentSender,
        action: AutofillAction
    ): Dataset {
        return Dataset.Builder(remoteView).run {
            fillWith(scenario, action, credentials = null)
            setAuthentication(intentSender)
            build()
        }
    }

    private fun makeMatchDataset(context: Context, file: File): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.Match).isEmpty()) return null
        val remoteView = makeFillMatchRemoteView(context, file, formOrigin)
        val intentSender = AutofillDecryptActivity.makeDecryptFileIntentSender(file, context)
        return makePlaceholderDataset(remoteView, intentSender, AutofillAction.Match)
    }

    private fun makeSearchDataset(context: Context): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.Search).isEmpty()) return null
        val remoteView = makeSearchAndFillRemoteView(context, formOrigin)
        val intentSender =
            AutofillFilterView.makeMatchAndDecryptFileIntentSender(context, formOrigin)
        return makePlaceholderDataset(remoteView, intentSender, AutofillAction.Search)
    }

    private fun makeGenerateDataset(context: Context): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.Generate).isEmpty()) return null
        val remoteView = makeGenerateAndFillRemoteView(context, formOrigin)
        val intentSender = AutofillSaveActivity.makeSaveIntentSender(context, null, formOrigin)
        return makePlaceholderDataset(remoteView, intentSender, AutofillAction.Generate)
    }

    private fun makeFillOtpFromSmsDataset(context: Context): Dataset? {
        if (scenario.fieldsToFillOn(AutofillAction.FillOtpFromSms).isEmpty()) return null
        if (!AutofillSmsActivity.shouldOfferFillFromSms(context)) return null
        val remoteView = makeFillOtpFromSmsRemoteView(context, formOrigin)
        val intentSender = AutofillSmsActivity.makeFillOtpFromSmsIntentSender(context)
        return makePlaceholderDataset(remoteView, intentSender, AutofillAction.FillOtpFromSms)
    }

    private fun makePublisherChangedDataset(
        context: Context,
        publisherChangedException: AutofillPublisherChangedException
    ): Dataset {
        val remoteView = makeWarningRemoteView(context)
        val intentSender = AutofillPublisherChangedActivity.makePublisherChangedIntentSender(
            context, publisherChangedException
        )
        return makePlaceholderDataset(remoteView, intentSender, AutofillAction.Match)
    }

    private fun makePublisherChangedResponse(
        context: Context,
        publisherChangedException: AutofillPublisherChangedException
    ): FillResponse {
        return FillResponse.Builder().run {
            addDataset(makePublisherChangedDataset(context, publisherChangedException))
            setIgnoredIds(*ignoredIds.toTypedArray())
            build()
        }
    }

    // TODO: Support multi-step authentication flows in apps via FLAG_DELAY_SAVE
    // See: https://developer.android.com/reference/android/service/autofill/SaveInfo#FLAG_DELAY_SAVE
    private fun makeSaveInfo(): SaveInfo? {
        if (!canBeSaved) return null
        check(saveFlags != null)
        val idsToSave = scenario.fieldsToSave.map { it.autofillId }.toTypedArray()
        if (idsToSave.isEmpty()) return null
        var saveDataTypes = SaveInfo.SAVE_DATA_TYPE_PASSWORD
        if (scenario.username != null) {
            saveDataTypes = saveDataTypes or SaveInfo.SAVE_DATA_TYPE_USERNAME
        }
        return SaveInfo.Builder(saveDataTypes, idsToSave).run {
            setFlags(saveFlags)
            build()
        }
    }

    private fun makeFillResponse(context: Context, matchedFiles: List<File>): FillResponse? {
        var hasDataset = false
        return FillResponse.Builder().run {
            for (file in matchedFiles) {
                makeMatchDataset(context, file)?.let {
                    hasDataset = true
                    addDataset(it)
                }
            }
            makeSearchDataset(context)?.let {
                hasDataset = true
                addDataset(it)
            }
            makeGenerateDataset(context)?.let {
                hasDataset = true
                addDataset(it)
            }
            makeFillOtpFromSmsDataset(context)?.let {
                hasDataset = true
                addDataset(it)
            }
            if (!hasDataset) return null
            makeSaveInfo()?.let { setSaveInfo(it) }
            setClientState(clientState)
            setIgnoredIds(*ignoredIds.toTypedArray())
            build()
        }
    }

    /**
     * Creates and returns a suitable [FillResponse] to the Autofill framework.
     */
    fun fillCredentials(context: Context, callback: FillCallback) {
        AutofillMatcher.getMatchesFor(context, formOrigin).fold(
            success = { matchedFiles ->
                callback.onSuccess(makeFillResponse(context, matchedFiles))
            },
            failure = { e ->
                e(e)
                callback.onSuccess(makePublisherChangedResponse(context, e))
            }
        )
    }

    companion object {
        fun makeFillInDataset(
            context: Context,
            credentials: Credentials,
            clientState: Bundle,
            action: AutofillAction
        ): Dataset {
            val remoteView = makePlaceholderRemoteView(context)
            val scenario = AutofillScenario.fromBundle(clientState)
            // Before Android P, Datasets used for fill-in had to come with a RemoteViews, even
            // though they are never shown.
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Dataset.Builder()
            } else {
                Dataset.Builder(remoteView)
            }
            return builder.run {
                if (scenario != null) fillWith(scenario, action, credentials)
                else e { "Failed to recover scenario from client state" }
                build()
            }
        }
    }
}
